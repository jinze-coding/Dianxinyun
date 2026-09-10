package com.example.siteplatform.siteaccess.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class VisitorLegacyReencryptionTool {
    static final String MIGRATION_KEY = "20260901_SITE_ACCESS_REENCRYPT_LEGACY_DEVELOPMENT_KEY_V1";
    static final String APPLY_CONFIRMATION = "APPLY_VISITOR_LEGACY_REENCRYPT_V1";
    private static final Logger log = LoggerFactory.getLogger(VisitorLegacyReencryptionTool.class);
    private static final Set<String> APPROVED_LEGACY_FIELDS = Set.of(
            "site_visit_invitation.token_encrypted",
            "site_visit_invitation.host_phone_encrypted",
            "site_visit_audit_log.after_snapshot_encrypted"
    );

    enum ContentKind { TEXT, PHONE, TOKEN_DIGEST, JSON_OBJECT }

    record FieldSpec(String table, String column, String digestColumn,
                     ContentKind contentKind, boolean hasUpdateTime) {}

    record EncryptedCell(FieldSpec spec, long id, String ciphertext, String expectedDigest) {}

    record Candidate(EncryptedCell cell, String plaintext) {}

    record Report(long total, long current, long legacy, long tokenMatches,
                  long unknown, String fingerprint, List<Candidate> candidates) {}

    static final List<FieldSpec> FIELD_SPECS = List.of(
            new FieldSpec("site_visit_invitation", "token_encrypted", "token_hash", ContentKind.TOKEN_DIGEST, true),
            new FieldSpec("site_visit_invitation", "host_phone_encrypted", null, ContentKind.PHONE, true),
            new FieldSpec("site_visit_invitation", "contact_phone_encrypted", null, ContentKind.PHONE, true),
            new FieldSpec("site_visit_person", "id_card_encrypted", null, ContentKind.TEXT, true),
            new FieldSpec("site_visit_person", "phone_encrypted", null, ContentKind.PHONE, true),
            new FieldSpec("site_visit_audit_log", "before_snapshot_encrypted", null, ContentKind.JSON_OBJECT, false),
            new FieldSpec("site_visit_audit_log", "after_snapshot_encrypted", null, ContentKind.JSON_OBJECT, false),

            new FieldSpec("site_visitor_profile", "owner_openid_encrypted", null, ContentKind.TEXT, true),
            new FieldSpec("site_visitor_profile", "contact_phone_encrypted", null, ContentKind.PHONE, true),
            new FieldSpec("site_visitor_profile_person", "id_card_encrypted", null, ContentKind.TEXT, true),
            new FieldSpec("site_visitor_profile_person", "phone_encrypted", null, ContentKind.PHONE, true),
            new FieldSpec("site_visitor_profile_audit_log", "before_snapshot_encrypted", null, ContentKind.JSON_OBJECT, false),
            new FieldSpec("site_visitor_profile_audit_log", "after_snapshot_encrypted", null, ContentKind.JSON_OBJECT, false),

            new FieldSpec("site_guard_visit_qr", "scene_token_encrypted", "scene_token_hash", ContentKind.TOKEN_DIGEST, true),
            new FieldSpec("site_guard_visit_registration", "contact_phone_encrypted", null, ContentKind.PHONE, true),
            new FieldSpec("site_guard_visit_person", "phone_encrypted", null, ContentKind.PHONE, true),
            new FieldSpec("site_guard_visit_audit_log", "before_snapshot_encrypted", null, ContentKind.JSON_OBJECT, false),
            new FieldSpec("site_guard_visit_audit_log", "after_snapshot_encrypted", null, ContentKind.JSON_OBJECT, false),

            new FieldSpec("site_meeting_visit_registration", "contact_phone_encrypted", null, ContentKind.PHONE, true),
            new FieldSpec("site_meeting_visit_person", "phone_encrypted", null, ContentKind.PHONE, true),
            new FieldSpec("site_meeting_visit_audit_log", "before_snapshot_encrypted", null, ContentKind.JSON_OBJECT, false),
            new FieldSpec("site_meeting_visit_audit_log", "after_snapshot_encrypted", null, ContentKind.JSON_OBJECT, false)
    );

    private final VisitorLegacyReencryptionStore store;
    private final TransactionOperations transactions;
    private final ObjectMapper objectMapper;
    private final VisitorDataCryptoService currentCrypto;
    private final VisitorDataCryptoService legacyCrypto;
    private final VisitorLegacyReencryptionProperties properties;

    VisitorLegacyReencryptionTool(VisitorLegacyReencryptionStore store,
                                  TransactionOperations transactions,
                                  ObjectMapper objectMapper,
                                  VisitorDataCryptoService currentCrypto,
                                  VisitorDataCryptoService legacyCrypto,
                                  VisitorLegacyReencryptionProperties properties) {
        this.store = store;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
        this.currentCrypto = currentCrypto;
        this.legacyCrypto = legacyCrypto;
        this.properties = properties;
    }

    Report execute() {
        if (properties.getMode() == null) {
            throw new IllegalStateException("访客历史密钥迁移必须显式指定 VERIFY 或 APPLY 模式");
        }
        store.verifyEncryptedColumnWhitelist();
        Report initial = scan(false);
        if (initial.unknown() != 0) {
            throw new IllegalStateException("存在无法由当前密钥或历史开发密钥解密的未知密文，迁移已阻断");
        }
        if (store.migrationMarkerExists(false)) {
            requirePostcondition(initial);
            log.info("访客历史密钥迁移标记已存在，后验校验通过：总数={}，当前密钥={}",
                    initial.total(), initial.current());
            return initial;
        }
        if (properties.getMode() == VisitorLegacyReencryptionProperties.Mode.VERIFY) {
            log.info("访客历史密钥 VERIFY 完成：总数={}，当前密钥={}，历史密钥={}，令牌摘要匹配={}，未知={}，候选指纹={}",
                    initial.total(), initial.current(), initial.legacy(), initial.tokenMatches(),
                    initial.unknown(), initial.fingerprint());
            return initial;
        }
        validateApplyExpectations(initial);
        return transactions.execute(status -> applyInTransaction());
    }

    private Report applyInTransaction() {
        if (store.migrationMarkerExists(true)) {
            Report alreadyApplied = scan(true);
            requirePostcondition(alreadyApplied);
            return alreadyApplied;
        }
        Report locked = scan(true);
        validateApplyExpectations(locked);

        List<String> replacements = new ArrayList<>(locked.candidates().size());
        for (Candidate candidate : locked.candidates()) {
            replacements.add(currentCrypto.encrypt(candidate.plaintext()));
        }
        for (int index = 0; index < locked.candidates().size(); index++) {
            Candidate candidate = locked.candidates().get(index);
            if (store.replaceCiphertext(candidate.cell(), replacements.get(index)) != 1) {
                throw new IllegalStateException("访客历史密文发生并发漂移，事务已取消");
            }
        }

        Report after = scan(true);
        requirePostcondition(after);
        if (after.total() != locked.total() || after.tokenMatches() != locked.tokenMatches()) {
            throw new IllegalStateException("访客历史密钥迁移后总数或令牌摘要校验数发生变化");
        }
        store.insertMigrationMarker();
        log.info("访客历史密钥 APPLY 完成：总数={}，重加密={}，令牌摘要匹配={}，迁移标记={}",
                after.total(), locked.legacy(), after.tokenMatches(), MIGRATION_KEY);
        return after;
    }

    private Report scan(boolean lock) {
        List<EncryptedCell> cells = store.loadEncryptedCells(lock);
        long current = 0;
        long legacy = 0;
        long tokenMatches = 0;
        long unknown = 0;
        List<Candidate> candidates = new ArrayList<>();

        for (EncryptedCell cell : cells) {
            String currentPlaintext = tryDecrypt(currentCrypto, cell.ciphertext());
            String legacyPlaintext = tryDecrypt(legacyCrypto, cell.ciphertext());
            if (currentPlaintext != null && legacyPlaintext == null) {
                validatePlaintext(cell, currentPlaintext);
                current++;
                if (cell.spec().contentKind() == ContentKind.TOKEN_DIGEST) tokenMatches++;
            } else if (currentPlaintext == null && legacyPlaintext != null) {
                String fieldKey = cell.spec().table() + "." + cell.spec().column();
                if (!APPROVED_LEGACY_FIELDS.contains(fieldKey)) {
                    throw new IllegalStateException("检测到非本次已核定范围的历史开发密钥字段，迁移已阻断：" + fieldKey);
                }
                validatePlaintext(cell, legacyPlaintext);
                legacy++;
                if (cell.spec().contentKind() == ContentKind.TOKEN_DIGEST) tokenMatches++;
                candidates.add(new Candidate(cell, legacyPlaintext));
            } else {
                unknown++;
            }
        }
        candidates.sort(Comparator
                .comparing((Candidate item) -> item.cell().spec().table())
                .thenComparing(item -> item.cell().spec().column())
                .thenComparingLong(item -> item.cell().id()));
        String fingerprint = candidateFingerprint(candidates);
        return new Report(cells.size(), current, legacy, tokenMatches, unknown,
                fingerprint, List.copyOf(candidates));
    }

    private void validatePlaintext(EncryptedCell cell, String plaintext) {
        if (cell.spec().contentKind() == ContentKind.TOKEN_DIGEST) {
            if (!plaintext.matches("^[A-Za-z0-9_-]{20,40}$") || !constantTimeHexEquals(
                    currentCrypto.digest(plaintext), cell.expectedDigest())) {
                throw new IllegalStateException("访客令牌或 scene 摘要校验失败，迁移已阻断");
            }
        } else if (cell.spec().contentKind() == ContentKind.PHONE) {
            if (!plaintext.matches("^1[3-9]\\d{9}$")) {
                throw new IllegalStateException("访客加密手机号码格式不正确，迁移已阻断");
            }
        } else if (cell.spec().contentKind() == ContentKind.JSON_OBJECT) {
            try {
                JsonNode node = objectMapper.reader()
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .readTree(plaintext);
                if (node == null || !node.isObject()) {
                    throw new IllegalStateException("访客加密审计快照不是 JSON 对象，迁移已阻断");
                }
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("访客加密审计快照不是有效 JSON，迁移已阻断", exception);
            }
        }
    }

    private void validateApplyExpectations(Report report) {
        if (!APPLY_CONFIRMATION.equals(properties.getConfirmation())) {
            throw new IllegalStateException("访客历史密钥 APPLY 确认短语不匹配");
        }
        requireExpected("总数", properties.getExpectedTotal(), report.total());
        requireExpected("当前密钥数", properties.getExpectedCurrent(), report.current());
        requireExpected("历史密钥数", properties.getExpectedLegacy(), report.legacy());
        requireExpected("令牌摘要匹配数", properties.getExpectedTokenMatches(), report.tokenMatches());
        String expectedFingerprint = normalizeFingerprint(properties.getExpectedFingerprint());
        if (!constantTimeHexEquals(expectedFingerprint, report.fingerprint())) {
            throw new IllegalStateException("访客历史密钥候选指纹不匹配");
        }
        if (report.unknown() != 0) {
            throw new IllegalStateException("存在无法由当前密钥或历史开发密钥解密的未知密文，迁移已阻断");
        }
        if (report.legacy() <= 0) {
            throw new IllegalStateException("APPLY 未发现历史开发密钥候选，拒绝写入迁移标记");
        }
    }

    private void requirePostcondition(Report report) {
        if (report.unknown() != 0 || report.legacy() != 0 || report.current() != report.total()) {
            throw new IllegalStateException("访客历史密钥迁移后验校验失败");
        }
    }

    private void requireExpected(String label, Long expected, long actual) {
        if (expected == null || expected < 0 || expected != actual) {
            throw new IllegalStateException("访客历史密钥 APPLY 期待" + label + "不匹配");
        }
    }

    private String normalizeFingerprint(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("访客历史密钥 APPLY 必须提供候选指纹");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("访客历史密钥候选指纹格式错误");
        }
        return normalized;
    }

    private String candidateFingerprint(List<Candidate> candidates) {
        MessageDigest digest = sha256Digest();
        for (Candidate candidate : candidates) {
            EncryptedCell cell = candidate.cell();
            String ciphertextDigest = HexFormat.of().formatHex(
                    sha256Digest().digest(cell.ciphertext().getBytes(StandardCharsets.UTF_8)));
            String line = cell.spec().table() + "|" + cell.spec().column() + "|"
                    + cell.id() + "|" + ciphertextDigest + "\n";
            digest.update(line.getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String tryDecrypt(VisitorDataCryptoService crypto, String ciphertext) {
        try {
            return crypto.decrypt(ciphertext);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean constantTimeHexEquals(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(
                left.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                right.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法生成访客历史密钥迁移指纹", exception);
        }
    }

    static Set<String> encryptedColumnWhitelist() {
        Set<String> columns = new LinkedHashSet<>();
        FIELD_SPECS.stream()
                .map(spec -> spec.table() + "." + spec.column())
                .sorted()
                .forEach(columns::add);
        return columns;
    }
}
