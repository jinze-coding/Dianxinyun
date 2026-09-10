package com.example.siteplatform.siteaccess.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisitorLegacyReencryptionToolTest {
    private VisitorDataCryptoService currentCrypto;
    private VisitorDataCryptoService legacyCrypto;
    private FakeStore store;
    private SnapshotTransactions transactions;
    private VisitorLegacyReencryptionProperties properties;

    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        currentCrypto = new VisitorDataCryptoService(
                "test-current-visitor-key-with-more-than-thirty-two-bytes", environment);
        legacyCrypto = VisitorDataCryptoService.legacyDevelopmentMigrationCipher();
        store = new FakeStore();
        transactions = new SnapshotTransactions(store);
        properties = new VisitorLegacyReencryptionProperties();
    }

    @Test
    void verifyClassifiesAllWhitelistedCellsWithoutWriting() {
        addLegacyTokenAndAudit();
        properties.setMode(VisitorLegacyReencryptionProperties.Mode.VERIFY);

        VisitorLegacyReencryptionTool.Report report = tool().execute();

        assertThat(report.total()).isEqualTo(2);
        assertThat(report.current()).isZero();
        assertThat(report.legacy()).isEqualTo(2);
        assertThat(report.tokenMatches()).isEqualTo(1);
        assertThat(report.unknown()).isZero();
        assertThat(report.fingerprint()).matches("[0-9a-f]{64}");
        assertThat(store.replaceCalls).isZero();
        assertThat(store.marker).isFalse();
        assertThat(transactions.executions).isZero();
    }

    @Test
    void applyReencryptsAtomicallyThenMarkerMakesRerunIdempotent() {
        addLegacyTokenAndAudit();
        VisitorLegacyReencryptionTool.Report verified = verifyReport();
        configureApply(verified);

        VisitorLegacyReencryptionTool.Report applied = tool().execute();

        assertThat(applied.total()).isEqualTo(2);
        assertThat(applied.current()).isEqualTo(2);
        assertThat(applied.legacy()).isZero();
        assertThat(store.marker).isTrue();
        assertThat(store.replaceCalls).isEqualTo(2);
        assertThat(transactions.executions).isEqualTo(1);
        store.cells.forEach(cell -> assertThat(currentCrypto.decrypt(cell.ciphertext)).isNotNull());

        VisitorLegacyReencryptionTool.Report rerun = tool().execute();
        assertThat(rerun.current()).isEqualTo(2);
        assertThat(store.replaceCalls).isEqualTo(2);
        assertThat(transactions.executions).isEqualTo(1);
    }

    @Test
    void applyRollsBackEveryReplacementWhenOptimisticUpdateFails() {
        addLegacyTokenAndAudit();
        List<String> originals = store.ciphertexts();
        VisitorLegacyReencryptionTool.Report verified = verifyReport();
        configureApply(verified);
        store.failReplaceCall = 2;

        assertThatThrownBy(() -> tool().execute())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("并发漂移");

        assertThat(store.ciphertexts()).containsExactlyElementsOf(originals);
        assertThat(store.marker).isFalse();
        assertThat(transactions.rollbacks).isEqualTo(1);
    }

    @Test
    void applyRejectsFingerprintDriftBetweenVerifyAndLockedScanBeforeWriting() {
        addLegacyTokenAndAudit();
        VisitorLegacyReencryptionTool.Report verified = verifyReport();
        configureApply(verified);
        transactions.beforeTransaction = () -> store.cells.get(1).ciphertext =
                legacyCrypto.encrypt("{\"ok\":true}");

        assertThatThrownBy(() -> tool().execute())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("候选指纹不匹配");

        assertThat(store.replaceCalls).isZero();
        assertThat(store.marker).isFalse();
    }

    @Test
    void unknownCiphertextBlocksEvenVerifyAndNeverStartsTransaction() {
        VisitorLegacyReencryptionTool.FieldSpec spec = spec("site_visit_invitation", "host_phone_encrypted");
        store.cells.add(new MutableCell(spec, 1L, "v1:not-valid-ciphertext", null));
        properties.setMode(VisitorLegacyReencryptionProperties.Mode.VERIFY);

        assertThatThrownBy(() -> tool().execute())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未知密文");
        assertThat(transactions.executions).isZero();
        assertThat(store.replaceCalls).isZero();
    }

    @Test
    void legacyCiphertextOutsideTheThreeAuditedProductionFieldsIsBlocked() {
        VisitorLegacyReencryptionTool.FieldSpec spec = spec("site_visitor_profile", "owner_openid_encrypted");
        store.cells.add(new MutableCell(spec, 1L, legacyCrypto.encrypt("legacy-identity"), null));
        properties.setMode(VisitorLegacyReencryptionProperties.Mode.VERIFY);

        assertThatThrownBy(() -> tool().execute())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("非本次已核定范围");
        assertThat(transactions.executions).isZero();
        assertThat(store.replaceCalls).isZero();
    }

    @Test
    void tokenDigestAndJsonObjectAreValidatedBeforeAnyWrite() {
        VisitorLegacyReencryptionTool.FieldSpec tokenSpec = spec("site_guard_visit_qr", "scene_token_encrypted");
        store.cells.add(new MutableCell(tokenSpec, 1L, currentCrypto.encrypt("scene"), currentCrypto.digest("other")));
        properties.setMode(VisitorLegacyReencryptionProperties.Mode.VERIFY);

        assertThatThrownBy(() -> tool().execute())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("摘要校验失败");
        assertThat(store.replaceCalls).isZero();

        store.cells.clear();
        VisitorLegacyReencryptionTool.FieldSpec auditSpec = spec("site_visit_audit_log", "after_snapshot_encrypted");
        store.cells.add(new MutableCell(auditSpec, 2L, legacyCrypto.encrypt("[]"), null));
        assertThatThrownBy(() -> tool().execute())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不是 JSON 对象");
    }

    @Test
    void phoneAndTokenShapesAreValidatedBeforeAnyWrite() {
        VisitorLegacyReencryptionTool.FieldSpec phoneSpec = spec("site_visit_invitation", "host_phone_encrypted");
        store.cells.add(new MutableCell(phoneSpec, 1L, currentCrypto.encrypt("not-a-phone"), null));
        properties.setMode(VisitorLegacyReencryptionProperties.Mode.VERIFY);

        assertThatThrownBy(() -> tool().execute())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("手机号码格式不正确");
        assertThat(store.replaceCalls).isZero();

        store.cells.clear();
        VisitorLegacyReencryptionTool.FieldSpec tokenSpec = spec("site_visit_invitation", "token_encrypted");
        store.cells.add(new MutableCell(tokenSpec, 2L, currentCrypto.encrypt("short"), currentCrypto.digest("short")));
        assertThatThrownBy(() -> tool().execute())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("摘要校验失败");
    }

    @Test
    void whitelistCoversAllKnownSingleProfileGuardAndMeetingCipherFields() {
        assertThat(VisitorLegacyReencryptionTool.FIELD_SPECS).hasSize(22);
        assertThat(VisitorLegacyReencryptionTool.encryptedColumnWhitelist())
                .contains("site_visit_invitation.token_encrypted",
                        "site_visitor_profile.owner_openid_encrypted",
                        "site_guard_visit_qr.scene_token_encrypted",
                        "site_meeting_visit_registration.contact_phone_encrypted",
                        "site_meeting_visit_audit_log.after_snapshot_encrypted");
    }

    private VisitorLegacyReencryptionTool.Report verifyReport() {
        properties.setMode(VisitorLegacyReencryptionProperties.Mode.VERIFY);
        return tool().execute();
    }

    private void configureApply(VisitorLegacyReencryptionTool.Report report) {
        properties.setMode(VisitorLegacyReencryptionProperties.Mode.APPLY);
        properties.setExpectedTotal(report.total());
        properties.setExpectedCurrent(report.current());
        properties.setExpectedLegacy(report.legacy());
        properties.setExpectedTokenMatches(report.tokenMatches());
        properties.setExpectedFingerprint(report.fingerprint());
        properties.setConfirmation(VisitorLegacyReencryptionTool.APPLY_CONFIRMATION);
    }

    private void addLegacyTokenAndAudit() {
        String token = "test-token-123456789012";
        store.cells.add(new MutableCell(
                spec("site_visit_invitation", "token_encrypted"),
                1L, legacyCrypto.encrypt(token), currentCrypto.digest(token)));
        store.cells.add(new MutableCell(
                spec("site_visit_audit_log", "after_snapshot_encrypted"),
                2L, legacyCrypto.encrypt("{\"ok\":true}"), null));
    }

    private VisitorLegacyReencryptionTool.FieldSpec spec(String table, String column) {
        return VisitorLegacyReencryptionTool.FIELD_SPECS.stream()
                .filter(item -> item.table().equals(table) && item.column().equals(column))
                .findFirst()
                .orElseThrow();
    }

    private VisitorLegacyReencryptionTool tool() {
        return new VisitorLegacyReencryptionTool(
                store, transactions, new ObjectMapper(), currentCrypto, legacyCrypto, properties);
    }

    private static final class MutableCell {
        private final VisitorLegacyReencryptionTool.FieldSpec spec;
        private final long id;
        private String ciphertext;
        private final String expectedDigest;

        private MutableCell(VisitorLegacyReencryptionTool.FieldSpec spec, long id,
                            String ciphertext, String expectedDigest) {
            this.spec = spec;
            this.id = id;
            this.ciphertext = ciphertext;
            this.expectedDigest = expectedDigest;
        }

        private MutableCell copy() {
            return new MutableCell(spec, id, ciphertext, expectedDigest);
        }
    }

    private static final class FakeStore implements VisitorLegacyReencryptionStore {
        private final List<MutableCell> cells = new ArrayList<>();
        private boolean marker;
        private int replaceCalls;
        private int failReplaceCall = -1;

        @Override
        public void verifyEncryptedColumnWhitelist() {}

        @Override
        public boolean migrationMarkerExists(boolean lock) {
            return marker;
        }

        @Override
        public List<VisitorLegacyReencryptionTool.EncryptedCell> loadEncryptedCells(boolean lock) {
            return cells.stream()
                    .map(cell -> new VisitorLegacyReencryptionTool.EncryptedCell(
                            cell.spec, cell.id, cell.ciphertext, cell.expectedDigest))
                    .toList();
        }

        @Override
        public int replaceCiphertext(VisitorLegacyReencryptionTool.EncryptedCell cell, String replacement) {
            replaceCalls++;
            if (replaceCalls == failReplaceCall) return 0;
            MutableCell current = cells.stream().filter(item -> item.id == cell.id()
                            && item.spec.equals(cell.spec())).findFirst().orElseThrow();
            if (!current.ciphertext.equals(cell.ciphertext())) return 0;
            current.ciphertext = replacement;
            return 1;
        }

        @Override
        public void insertMigrationMarker() {
            if (marker) throw new IllegalStateException("duplicate marker");
            marker = true;
        }

        private List<MutableCell> snapshot() {
            return cells.stream().map(MutableCell::copy).toList();
        }

        private void restore(List<MutableCell> snapshot, boolean markerSnapshot, int replaceCallsSnapshot) {
            cells.clear();
            snapshot.stream().map(MutableCell::copy).forEach(cells::add);
            marker = markerSnapshot;
            replaceCalls = replaceCallsSnapshot;
        }

        private List<String> ciphertexts() {
            return cells.stream().map(cell -> cell.ciphertext).toList();
        }
    }

    private static final class SnapshotTransactions implements TransactionOperations {
        private final FakeStore store;
        private int executions;
        private int rollbacks;
        private Runnable beforeTransaction = () -> {};

        private SnapshotTransactions(FakeStore store) {
            this.store = store;
        }

        @Override
        public <T> T execute(TransactionCallback<T> action) throws TransactionException {
            executions++;
            beforeTransaction.run();
            List<MutableCell> snapshot = store.snapshot();
            boolean markerSnapshot = store.marker;
            int replaceCallsSnapshot = store.replaceCalls;
            TransactionStatus status = new SimpleTransactionStatus();
            try {
                return action.doInTransaction(status);
            } catch (RuntimeException | Error exception) {
                rollbacks++;
                store.restore(snapshot, markerSnapshot, replaceCallsSnapshot);
                throw exception;
            }
        }

    }
}
