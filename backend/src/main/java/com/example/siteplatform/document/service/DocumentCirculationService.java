package com.example.siteplatform.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.document.dto.DocumentDistributionCreateRequest;
import com.example.siteplatform.document.dto.DocumentDistributionSettingsRequest;
import com.example.siteplatform.document.dto.DocumentIncomingBatchSaveRequest;
import com.example.siteplatform.document.dto.DocumentIncomingItemRequest;
import com.example.siteplatform.document.dto.DocumentIncomingPublishRequest;
import com.example.siteplatform.document.dto.DocumentRecipientCopyRequest;
import com.example.siteplatform.document.dto.DocumentRecipientRequest;
import com.example.siteplatform.document.entity.DocumentCirculationEvent;
import com.example.siteplatform.document.entity.DocumentDistributionBatch;
import com.example.siteplatform.document.entity.DocumentDistributionItem;
import com.example.siteplatform.document.entity.DocumentDistributionRecipient;
import com.example.siteplatform.document.entity.DocumentDistributionRecipientItem;
import com.example.siteplatform.document.entity.DocumentIncomingBatch;
import com.example.siteplatform.document.entity.DocumentIncomingItem;
import com.example.siteplatform.document.entity.ProjectDocument;
import com.example.siteplatform.document.entity.ProjectDocumentVersion;
import com.example.siteplatform.document.mapper.DocumentCirculationEventMapper;
import com.example.siteplatform.document.mapper.DocumentDistributionBatchMapper;
import com.example.siteplatform.document.mapper.DocumentDistributionItemMapper;
import com.example.siteplatform.document.mapper.DocumentDistributionRecipientItemMapper;
import com.example.siteplatform.document.mapper.DocumentDistributionRecipientMapper;
import com.example.siteplatform.document.mapper.DocumentIncomingBatchMapper;
import com.example.siteplatform.document.mapper.DocumentIncomingItemMapper;
import com.example.siteplatform.document.mapper.ProjectDocumentMapper;
import com.example.siteplatform.document.mapper.ProjectDocumentVersionMapper;
import com.example.siteplatform.document.vo.DocumentDistributionBatchVO;
import com.example.siteplatform.document.vo.DocumentDistributionItemVO;
import com.example.siteplatform.document.vo.DocumentDistributionRecipientVO;
import com.example.siteplatform.document.vo.DocumentIncomingBatchVO;
import com.example.siteplatform.document.vo.DocumentIncomingItemVO;
import com.example.siteplatform.document.vo.DocumentMatchCandidateVO;
import com.example.siteplatform.document.vo.DocumentRecipientOptionVO;
import com.example.siteplatform.file.constant.FileStatus;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.security.FileUploadPolicy;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.file.storage.StoredFile;
import com.example.siteplatform.notification.service.UserNotificationService;
import com.example.siteplatform.project.dto.ProjectMemberVO;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.mapper.SysUserProjectRoleMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.example.siteplatform.system.entity.SystemRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DocumentCirculationService {
    public static final String ROUTE_CODE = "DOCUMENT_DISTRIBUTION_DETAIL";
    public static final String BUSINESS_TYPE = "DOCUMENT_DISTRIBUTION";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter NUMBER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter NOTIFICATION_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<String> FORMAL_TYPES = Set.of("DRAWING", "TECHNICAL_DOCUMENT");
    private static final Set<String> MATCH_MODES = Set.of("NEW_DOCUMENT", "NEW_VERSION");
    private static final Set<String> CHANNELS = Set.of("ELECTRONIC", "PAPER", "BOTH");

    private final DocumentIncomingBatchMapper incomingBatchMapper;
    private final DocumentIncomingItemMapper incomingItemMapper;
    private final DocumentDistributionBatchMapper distributionBatchMapper;
    private final DocumentDistributionItemMapper distributionItemMapper;
    private final DocumentDistributionRecipientMapper recipientMapper;
    private final DocumentDistributionRecipientItemMapper recipientItemMapper;
    private final DocumentCirculationEventMapper eventMapper;
    private final ProjectDocumentMapper documentMapper;
    private final ProjectDocumentVersionMapper versionMapper;
    private final FileResourceMapper fileMapper;
    private final SysUserMapper userMapper;
    private final SysUserProjectMapper userProjectMapper;
    private final SysUserProjectRoleMapper userProjectRoleMapper;
    private final ProjectPermissionService permissionService;
    private final UserNotificationService notificationService;
    private final DocumentSceneService sceneService;
    private final FileStorageManager storageManager;
    private final ObjectMapper objectMapper;

    public DocumentCirculationService(
            DocumentIncomingBatchMapper incomingBatchMapper,
            DocumentIncomingItemMapper incomingItemMapper,
            DocumentDistributionBatchMapper distributionBatchMapper,
            DocumentDistributionItemMapper distributionItemMapper,
            DocumentDistributionRecipientMapper recipientMapper,
            DocumentDistributionRecipientItemMapper recipientItemMapper,
            DocumentCirculationEventMapper eventMapper,
            ProjectDocumentMapper documentMapper,
            ProjectDocumentVersionMapper versionMapper,
            FileResourceMapper fileMapper,
            SysUserMapper userMapper,
            SysUserProjectMapper userProjectMapper,
            SysUserProjectRoleMapper userProjectRoleMapper,
            ProjectPermissionService permissionService,
            UserNotificationService notificationService,
            DocumentSceneService sceneService,
            FileStorageManager storageManager,
            ObjectMapper objectMapper) {
        this.incomingBatchMapper = incomingBatchMapper;
        this.incomingItemMapper = incomingItemMapper;
        this.distributionBatchMapper = distributionBatchMapper;
        this.distributionItemMapper = distributionItemMapper;
        this.recipientMapper = recipientMapper;
        this.recipientItemMapper = recipientItemMapper;
        this.eventMapper = eventMapper;
        this.documentMapper = documentMapper;
        this.versionMapper = versionMapper;
        this.fileMapper = fileMapper;
        this.userMapper = userMapper;
        this.userProjectMapper = userProjectMapper;
        this.userProjectRoleMapper = userProjectRoleMapper;
        this.permissionService = permissionService;
        this.notificationService = notificationService;
        this.sceneService = sceneService;
        this.storageManager = storageManager;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DocumentIncomingBatchVO createIncoming(DocumentIncomingBatchSaveRequest request, SysUser user) {
        requirePermission(user, request.getProjectId(), SystemPermissionCodes.DOCUMENT_RECEIVE);
        LocalDateTime now = now();
        DocumentIncomingBatch batch = new DocumentIncomingBatch();
        batch.setProjectId(request.getProjectId());
        batch.setIncomingNo(newNumber("SW"));
        copyIncomingFields(batch, request);
        batch.setReceiverId(user.getId());
        batch.setReceiverName(displayName(user));
        batch.setStatus("DRAFT");
        batch.setVersion(0);
        batch.setCreateTime(now);
        batch.setUpdateTime(now);
        requireSingleWrite(incomingBatchMapper.insert(batch), "收文草稿新增");
        replaceDraftItems(batch, request.getItems(), user);
        return incomingDetail(batch.getId(), user);
    }

    @Transactional
    public DocumentIncomingBatchVO updateIncoming(Long id, DocumentIncomingBatchSaveRequest request, SysUser user) {
        requirePermission(user, request.getProjectId(), SystemPermissionCodes.DOCUMENT_RECEIVE);
        DocumentIncomingBatch batch = incomingBatchMapper.selectForUpdate(id);
        requireDraft(batch, request.getProjectId());
        if (request.getExpectedVersion() == null || !Objects.equals(request.getExpectedVersion(), batch.getVersion())) {
            throw BusinessException.of(409, "收文草稿已被其他操作更新，请刷新后重试");
        }
        copyIncomingFields(batch, request);
        batch.setVersion(batch.getVersion() + 1);
        batch.setUpdateTime(now());
        requireSingleWrite(incomingBatchMapper.updateById(batch), "收文草稿更新");
        replaceDraftItems(batch, request.getItems(), user);
        return incomingDetail(id, user);
    }

    public PageResult<DocumentIncomingBatchVO> listIncoming(Long projectId, String status,
                                                             int pageNo, int pageSize, SysUser user) {
        requirePermission(user, projectId, SystemPermissionCodes.DOCUMENT_CIRCULATION_VIEW);
        int page = Math.max(1, pageNo);
        int size = Math.max(1, Math.min(100, pageSize));
        LambdaQueryWrapper<DocumentIncomingBatch> query = new LambdaQueryWrapper<DocumentIncomingBatch>()
                .eq(DocumentIncomingBatch::getProjectId, projectId)
                .eq(StringUtils.hasText(status), DocumentIncomingBatch::getStatus, normalize(status))
                .orderByDesc(DocumentIncomingBatch::getReceivedAt)
                .orderByDesc(DocumentIncomingBatch::getId);
        Page<DocumentIncomingBatch> result = incomingBatchMapper.selectPage(new Page<>(page, size), query);
        return PageResult.of(page, size, result.getTotal(), result.getRecords().stream()
                .map(this::toIncomingSummary).toList());
    }

    public DocumentIncomingBatchVO incomingDetail(Long id, SysUser user) {
        DocumentIncomingBatch batch = requireIncoming(id);
        requireAnyCirculationRead(user, batch.getProjectId());
        DocumentIncomingBatchVO vo = toIncomingSummary(batch);
        vo.setItems(incomingItemMapper.selectList(new LambdaQueryWrapper<DocumentIncomingItem>()
                        .eq(DocumentIncomingItem::getBatchId, id)
                        .orderByAsc(DocumentIncomingItem::getItemOrder))
                .stream().map(this::toIncomingItemVO).toList());
        DocumentDistributionBatch distribution = distributionBatchMapper.selectOne(
                new LambdaQueryWrapper<DocumentDistributionBatch>()
                        .eq(DocumentDistributionBatch::getIncomingBatchId, id)
                        .orderByDesc(DocumentDistributionBatch::getId).last("LIMIT 1"));
        if (distribution != null) vo.setDistributionBatchId(distribution.getId());
        return vo;
    }

    public List<DocumentMatchCandidateVO> matchCandidates(Long projectId, String documentType,
                                                           String documentNo, SysUser user) {
        requirePermission(user, projectId, SystemPermissionCodes.DOCUMENT_RECEIVE);
        String type = normalizeFormalType(documentType);
        String identity = normalizedIdentity(documentNo);
        if (!StringUtils.hasText(identity)) return List.of();
        return documentMapper.selectList(new LambdaQueryWrapper<ProjectDocument>()
                        .eq(ProjectDocument::getProjectId, projectId)
                        .eq(ProjectDocument::getDocumentType, type)
                        .eq(ProjectDocument::getStatus, ProjectDocumentService.STATUS_ACTIVE))
                .stream()
                .filter(item -> identity.equals(normalizedIdentity(item.getDocumentNo())))
                .map(item -> {
                    DocumentMatchCandidateVO vo = new DocumentMatchCandidateVO();
                    vo.setDocumentId(item.getId());
                    vo.setDocumentNo(item.getDocumentNo());
                    vo.setTitle(item.getTitle());
                    vo.setDocumentType(item.getDocumentType());
                    vo.setCurrentVersionId(item.getCurrentVersionId());
                    ProjectDocumentVersion version = item.getCurrentVersionId() == null ? null
                            : versionMapper.selectById(item.getCurrentVersionId());
                    if (version != null) {
                        vo.setCurrentVersionNo(version.getVersionNo());
                        vo.setCurrentExternalRevision(version.getExternalRevision());
                    }
                    return vo;
                }).toList();
    }

    @Transactional
    public DocumentDistributionBatchVO publishIncoming(Long id, DocumentIncomingPublishRequest request,
                                                        SysUser user, HttpServletRequest httpRequest) {
        DocumentIncomingBatch batch = incomingBatchMapper.selectForUpdate(id);
        if (batch == null) throw BusinessException.notFound("收文批次不存在");
        requirePermission(user, batch.getProjectId(), SystemPermissionCodes.DOCUMENT_RECEIVE);
        requireDraft(batch, batch.getProjectId());
        if (request.getExpectedVersion() == null || !Objects.equals(request.getExpectedVersion(), batch.getVersion())) {
            throw BusinessException.of(409, "收文草稿已被其他操作更新，请刷新后重试");
        }
        Map<Long, Long> oldItemFileIds = incomingItems(id).stream().collect(Collectors.toMap(
                DocumentIncomingItem::getId, DocumentIncomingItem::getFileResourceId));
        replaceDraftItems(batch, request.getItems(), user);
        List<DocumentIncomingItem> items = incomingItems(id);
        if (items.isEmpty()) throw new BusinessException("收文批次至少包含一份文件");
        Map<Long, Long> newItemIdsByFile = items.stream().collect(Collectors.toMap(
                DocumentIncomingItem::getFileResourceId, DocumentIncomingItem::getId));
        remapRecipientCopyItemIds(request.getDistribution(), oldItemFileIds, newItemIdsByFile);

        Map<Long, Long> previousVersionByItem = new LinkedHashMap<>();
        List<PublishedItem> published = new ArrayList<>();
        for (DocumentIncomingItem item : items) {
            PublishedItem value = publishIncomingItem(batch, item, user);
            published.add(value);
            if (value.previousVersionId() != null) previousVersionByItem.put(item.getId(), value.previousVersionId());
        }
        Set<Long> mandatoryUsers = mandatoryRecipientUsers(previousVersionByItem.values(), batch.getProjectId());
        Set<Long> excludedUsers = excludedHistoricalRecipientUsers(previousVersionByItem.values(), batch.getProjectId());
        DocumentDistributionBatch distribution = createDistribution(
                batch.getProjectId(), batch.getId(), published, request.getDistribution(),
                mandatoryUsers, user, httpRequest);

        LocalDateTime now = now();
        batch.setStatus("PUBLISHED");
        batch.setPublishedBy(user.getId());
        batch.setPublishedByName(displayName(user));
        batch.setPublishedTime(now);
        batch.setVersion(batch.getVersion() + 1);
        batch.setUpdateTime(now);
        requireSingleWrite(incomingBatchMapper.updateById(batch), "收文批次发布");
        recordEvent(batch.getProjectId(), batch.getId(), distribution.getId(), null, null, null,
                user, "INCOMING_PUBLISHED", null, "SUCCESS", "收文批次发布并生成发放批次", null, httpRequest);
        for (Long excludedUserId : excludedUsers) {
            SysUser excluded = userMapper.selectById(excludedUserId);
            recordEvent(batch.getProjectId(), batch.getId(), distribution.getId(), null, null, null,
                    excluded, "HISTORICAL_RECIPIENT_EXCLUDED", null, "SUCCESS",
                    "历史接收人已失效或无资料权限，本次未通知", Map.of("userId", excludedUserId), httpRequest);
        }
        return distributionDetail(distribution.getId(), user);
    }

    private void remapRecipientCopyItemIds(DocumentDistributionSettingsRequest settings,
                                           Map<Long, Long> oldItemFileIds,
                                           Map<Long, Long> newItemIdsByFile) {
        if (settings == null || settings.getRecipients() == null) return;
        for (DocumentRecipientRequest recipient : settings.getRecipients()) {
            if (recipient.getCopies() == null) continue;
            for (DocumentRecipientCopyRequest copy : recipient.getCopies()) {
                if (copy.getIncomingItemId() == null) continue;
                Long fileId = oldItemFileIds.get(copy.getIncomingItemId());
                Long remapped = fileId == null ? null : newItemIdsByFile.get(fileId);
                if (remapped == null) throw BusinessException.of(409, "接收人份数配置引用的收文文件已变化");
                copy.setIncomingItemId(remapped);
            }
        }
    }

    @Transactional
    public DocumentDistributionBatchVO createDistribution(DocumentDistributionCreateRequest request,
                                                           SysUser user, HttpServletRequest httpRequest) {
        requirePermission(user, request.getProjectId(), SystemPermissionCodes.DOCUMENT_ISSUE);
        List<Long> distinctVersionIds = request.getVersionIds().stream().distinct().toList();
        List<PublishedItem> items = new ArrayList<>();
        for (Long versionId : distinctVersionIds) {
            ProjectDocumentVersion version = versionMapper.selectById(versionId);
            if (version == null) throw BusinessException.notFound("发放版本不存在");
            ProjectDocument document = documentMapper.selectForUpdate(version.getDocumentId());
            if (document == null || !request.getProjectId().equals(document.getProjectId())) {
                throw new BusinessException("发放版本不属于当前项目");
            }
            if (!FORMAL_TYPES.contains(document.getDocumentType())) throw new BusinessException("仅正式图纸和技术文件可发放");
            if (!Objects.equals(document.getCurrentVersionId(), versionId) || !"CURRENT".equals(version.getVersionStatus())) {
                throw BusinessException.of(409, "只能再次发放资料的当前版本");
            }
            items.add(new PublishedItem(null, document, version, null));
        }
        DocumentDistributionBatch batch = createDistribution(request.getProjectId(), null, items,
                request.getDistribution(), Set.of(), user, httpRequest);
        return distributionDetail(batch.getId(), user);
    }

    public PageResult<DocumentDistributionBatchVO> listDistributions(Long projectId, String status,
                                                                      int pageNo, int pageSize, SysUser user) {
        requirePermission(user, projectId, SystemPermissionCodes.DOCUMENT_CIRCULATION_VIEW);
        int page = Math.max(1, pageNo);
        int size = Math.max(1, Math.min(100, pageSize));
        LambdaQueryWrapper<DocumentDistributionBatch> query = new LambdaQueryWrapper<DocumentDistributionBatch>()
                .eq(DocumentDistributionBatch::getProjectId, projectId)
                .eq(StringUtils.hasText(status), DocumentDistributionBatch::getStatus, normalize(status))
                .orderByDesc(DocumentDistributionBatch::getPublishedTime)
                .orderByDesc(DocumentDistributionBatch::getId);
        Page<DocumentDistributionBatch> result = distributionBatchMapper.selectPage(new Page<>(page, size), query);
        return PageResult.of(page, size, result.getTotal(), result.getRecords().stream()
                .map(batch -> toDistributionVO(batch, null, false)).toList());
    }

    public DocumentDistributionBatchVO distributionDetail(Long id, SysUser user) {
        DocumentDistributionBatch batch = requireDistribution(id);
        requirePermission(user, batch.getProjectId(), SystemPermissionCodes.DOCUMENT_CIRCULATION_VIEW);
        return toDistributionVO(batch, null, true);
    }

    public DocumentDistributionBatchVO myDistributionDetail(Long id, SysUser user) {
        DocumentDistributionBatch batch = requireDistribution(id);
        DocumentDistributionRecipient recipient = recipientMapper.selectOne(
                new LambdaQueryWrapper<DocumentDistributionRecipient>()
                        .eq(DocumentDistributionRecipient::getBatchId, id)
                        .eq(DocumentDistributionRecipient::getUserId, user.getId()).last("LIMIT 1"));
        requireRecipientAccess(batch, recipient, user);
        return toDistributionVO(batch, recipient, false);
    }

    public DocumentDistributionBatchVO scan(String rawScene, SysUser user) {
        String scene = sceneService.normalizeScene(rawScene);
        DocumentDistributionBatch batch = distributionBatchMapper.selectBySceneDigest(sceneService.digest(scene));
        if (batch == null || !"ACTIVE".equals(batch.getQrStatus()) || "VOIDED".equals(batch.getStatus())) {
            throw BusinessException.notFound("图纸领取二维码不存在或已失效");
        }
        DocumentDistributionRecipient recipient = recipientMapper.selectOne(
                new LambdaQueryWrapper<DocumentDistributionRecipient>()
                        .eq(DocumentDistributionRecipient::getBatchId, batch.getId())
                        .eq(DocumentDistributionRecipient::getUserId, user.getId()).last("LIMIT 1"));
        requireRecipientAccess(batch, recipient, user);
        if ("ELECTRONIC".equals(recipient.getChannel())) {
            throw BusinessException.forbidden("该任务为电子签收，不需要扫描纸质领取码");
        }
        return toDistributionVO(batch, recipient, false);
    }

    public String distributionQrSvg(Long id, SysUser user) {
        DocumentDistributionBatch batch = requireDistribution(id);
        requirePermission(user, batch.getProjectId(), SystemPermissionCodes.DOCUMENT_ISSUE);
        if (!"ACTIVE".equals(batch.getQrStatus()) || "VOIDED".equals(batch.getStatus())) {
            throw new BusinessException("发放批次二维码已失效");
        }
        return sceneService.qrSvg(sceneService.decrypt(batch.getQrSceneCiphertext()));
    }

    public List<DocumentRecipientOptionVO> recipientCandidates(Long projectId, List<Long> versionIds, SysUser user) {
        requirePermission(user, projectId, SystemPermissionCodes.DOCUMENT_ISSUE);
        Set<Long> mandatory = mandatoryRecipientUsers(versionIds == null ? List.of() : versionIds, projectId);
        return activeDocumentMembers(projectId).values().stream()
                .map(member -> {
                    DocumentRecipientOptionVO vo = new DocumentRecipientOptionVO();
                    vo.setUserId(member.getUserId());
                    vo.setUsername(member.getUsername());
                    vo.setRealName(displayName(member));
                    vo.setPhone(member.getPhone());
                    vo.setRoleNames(roleNames(member.getUserId(), projectId));
                    vo.setMandatory(mandatory.contains(member.getUserId()));
                    return vo;
                }).sorted(Comparator.comparing(DocumentRecipientOptionVO::getMandatory).reversed()
                        .thenComparing(DocumentRecipientOptionVO::getRealName,
                                Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    @Transactional
    public DocumentDistributionBatchVO confirm(Long batchId, String rawScene, MultipartFile signature,
                                                SysUser user, HttpServletRequest request) {
        DocumentDistributionBatch batch = distributionBatchMapper.selectForUpdate(batchId);
        if (batch == null) throw BusinessException.notFound("发放批次不存在");
        DocumentDistributionRecipient recipient = recipientMapper.selectOne(
                new LambdaQueryWrapper<DocumentDistributionRecipient>()
                        .eq(DocumentDistributionRecipient::getBatchId, batchId)
                        .eq(DocumentDistributionRecipient::getUserId, user.getId()).last("LIMIT 1"));
        if (recipient == null) throw BusinessException.forbidden("您不在该发放批次接收名单内");
        recipient = recipientMapper.selectForUpdate(recipient.getId());
        requireRecipientAccess(batch, recipient, user);
        if ("CONFIRMED".equals(recipient.getStatus())) return toDistributionVO(batch, recipient, false);
        if ("DISPUTED".equals(recipient.getStatus())) throw BusinessException.of(409, "已提交异议，不能继续签收");
        if (!Set.of("PUBLISHED", "DISPUTED").contains(batch.getStatus())) {
            throw BusinessException.of(409, "当前发放批次不能签收");
        }
        boolean paper = !"ELECTRONIC".equals(recipient.getChannel());
        if (paper) verifyScene(batch, rawScene);
        boolean signatureRequired = "ELECTRONIC".equals(recipient.getChannel())
                ? truth(batch.getElectronicSignatureRequired())
                : truth(batch.getPaperSignatureRequired()) || ("BOTH".equals(recipient.getChannel())
                && truth(batch.getElectronicSignatureRequired()));
        if (signatureRequired && (signature == null || signature.isEmpty())) {
            throw new BusinessException("本批次要求提交手写签名");
        }
        Long signatureFileId = null;
        if (signature != null && !signature.isEmpty()) {
            FileUploadPolicy.validateReceiptSignature(signature);
            signatureFileId = storeSignature(batch, recipient, signature, user);
        }
        LocalDateTime now = now();
        recipient.setStatus("CONFIRMED");
        recipient.setConfirmedTime(now);
        recipient.setSignatureFileId(signatureFileId);
        recipient.setUpdateTime(now);
        requireSingleWrite(recipientMapper.updateById(recipient), "图纸签收确认");
        List<DocumentDistributionItem> items = distributionItems(batchId);
        for (DocumentDistributionItem item : items) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("systemVersionNo", item.getSystemVersionNo());
            data.put("externalRevision", item.getExternalRevisionSnapshot());
            data.put("paperCopyCount", paperCopyCount(recipient.getId(), item.getId()));
            data.put("signatureFileId", signatureFileId);
            data.put("usernameAtConfirm", user.getUsername());
            data.put("memberStatusAtConfirm", permissionService.getProjectAccessStatus(
                    user.getId(), batch.getProjectId()));
            recordEvent(batch.getProjectId(), batch.getIncomingBatchId(), batchId, recipient.getId(),
                    item.getDocumentId(), item.getVersionId(), user, "CONFIRM", recipient.getChannel(),
                    "SUCCESS", "接收人确认图纸发放", data, request);
        }
        long remaining = recipientMapper.selectCount(new LambdaQueryWrapper<DocumentDistributionRecipient>()
                .eq(DocumentDistributionRecipient::getBatchId, batchId)
                .ne(DocumentDistributionRecipient::getStatus, "CONFIRMED"));
        if (remaining == 0) {
            batch.setStatus("COMPLETED");
            batch.setVersion(batch.getVersion() + 1);
            batch.setUpdateTime(now);
            requireSingleWrite(distributionBatchMapper.updateById(batch), "发放批次完成");
        }
        return toDistributionVO(batch, recipient, false);
    }

    @Transactional
    public DocumentDistributionBatchVO dispute(Long batchId, String note, SysUser user,
                                                HttpServletRequest request) {
        DocumentDistributionBatch batch = distributionBatchMapper.selectForUpdate(batchId);
        if (batch == null) throw BusinessException.notFound("发放批次不存在");
        DocumentDistributionRecipient recipient = recipientMapper.selectOne(
                new LambdaQueryWrapper<DocumentDistributionRecipient>()
                        .eq(DocumentDistributionRecipient::getBatchId, batchId)
                        .eq(DocumentDistributionRecipient::getUserId, user.getId()).last("LIMIT 1"));
        if (recipient == null) throw BusinessException.forbidden("您不在该发放批次接收名单内");
        recipient = recipientMapper.selectForUpdate(recipient.getId());
        requireRecipientAccess(batch, recipient, user);
        if ("CONFIRMED".equals(recipient.getStatus())) throw BusinessException.of(409, "已完成签收，不能提交异议");
        if ("DISPUTED".equals(recipient.getStatus())) return toDistributionVO(batch, recipient, false);
        String normalizedNote = requiredText(note, 500, "异议说明");
        LocalDateTime now = now();
        recipient.setStatus("DISPUTED");
        recipient.setDisputeNote(normalizedNote);
        recipient.setDisputeTime(now);
        recipient.setUpdateTime(now);
        requireSingleWrite(recipientMapper.updateById(recipient), "图纸签收异议");
        batch.setStatus("DISPUTED");
        batch.setVersion(batch.getVersion() + 1);
        batch.setUpdateTime(now);
        requireSingleWrite(distributionBatchMapper.updateById(batch), "发放批次异议状态更新");
        recordEvent(batch.getProjectId(), batch.getIncomingBatchId(), batchId, recipient.getId(),
                null, null, user, "DISPUTE", recipient.getChannel(), "SUCCESS", normalizedNote, null, request);
        notificationService.notify(batch.getPublishedBy(), batch.getProjectId(), BUSINESS_TYPE, batchId,
                "DOCUMENT_DISTRIBUTION_DISPUTED", "图纸签收异议",
                displayName(user) + "对发放批次" + batch.getDistributionNo() + "提出异议",
                "document-distribution-dispute:" + recipient.getId(), ROUTE_CODE,
                routeParams(batchId));
        return toDistributionVO(batch, recipient, false);
    }

    @Transactional
    public void voidDistribution(Long id, String reason, SysUser user, HttpServletRequest request) {
        DocumentDistributionBatch batch = distributionBatchMapper.selectForUpdate(id);
        if (batch == null) throw BusinessException.notFound("发放批次不存在");
        requirePermission(user, batch.getProjectId(), SystemPermissionCodes.DOCUMENT_ISSUE);
        if ("VOIDED".equals(batch.getStatus())) return;
        String normalizedReason = requiredText(reason, 500, "作废原因");
        LocalDateTime now = now();
        batch.setStatus("VOIDED");
        batch.setQrStatus("VOIDED");
        batch.setVoidReason(normalizedReason);
        batch.setVoidedBy(user.getId());
        batch.setVoidedByName(displayName(user));
        batch.setVoidedTime(now);
        batch.setVersion(batch.getVersion() + 1);
        batch.setUpdateTime(now);
        requireSingleWrite(distributionBatchMapper.updateById(batch), "发放批次作废");
        recordEvent(batch.getProjectId(), batch.getIncomingBatchId(), id, null, null, null,
                user, "DISTRIBUTION_VOIDED", null, "SUCCESS", normalizedReason, null, request);
    }

    @Transactional
    public void voidIncoming(Long id, String reason, SysUser user, HttpServletRequest request) {
        DocumentIncomingBatch batch = incomingBatchMapper.selectForUpdate(id);
        if (batch == null) throw BusinessException.notFound("收文批次不存在");
        requirePermission(user, batch.getProjectId(), SystemPermissionCodes.DOCUMENT_RECEIVE);
        if ("PUBLISHED".equals(batch.getStatus())) throw new BusinessException("已发布收文须通过发放批次纠错流程处理");
        if ("VOIDED".equals(batch.getStatus())) return;
        LocalDateTime now = now();
        batch.setStatus("VOIDED");
        batch.setVoidReason(requiredText(reason, 500, "作废原因"));
        batch.setVoidedBy(user.getId());
        batch.setVoidedByName(displayName(user));
        batch.setVoidedTime(now);
        batch.setVersion(batch.getVersion() + 1);
        batch.setUpdateTime(now);
        requireSingleWrite(incomingBatchMapper.updateById(batch), "收文批次作废");
        recordEvent(batch.getProjectId(), id, null, null, null, null, user,
                "INCOMING_VOIDED", null, "SUCCESS", batch.getVoidReason(), null, request);
    }

    public void recordDocumentAccess(ProjectDocument document, ProjectDocumentVersion version,
                                     SysUser user, boolean preview, boolean success,
                                     HttpServletRequest request) {
        recordDocumentAccess(document, version, user, preview, success, request, null);
    }

    public void recordDocumentAccess(ProjectDocument document, ProjectDocumentVersion version,
                                     SysUser user, boolean preview, boolean success,
                                     HttpServletRequest request, Long distributionBatchHint) {
        if (document == null || version == null || !FORMAL_TYPES.contains(document.getDocumentType())) return;
        List<DocumentDistributionItem> issued = distributionItemMapper.selectList(
                new LambdaQueryWrapper<DocumentDistributionItem>()
                        .eq(DocumentDistributionItem::getVersionId, version.getId())
                        .orderByDesc(DocumentDistributionItem::getId));
        Long distributionId = null;
        DocumentDistributionRecipient recipient = null;
        if (distributionBatchHint != null) {
            boolean containsVersion = issued.stream()
                    .anyMatch(item -> distributionBatchHint.equals(item.getBatchId()));
            if (containsVersion) {
                DocumentDistributionRecipient hintedRecipient = recipientForUser(distributionBatchHint, user.getId());
                boolean manager = permissionService.hasSystemPermission(user.getId(), document.getProjectId(),
                        SystemPermissionCodes.DOCUMENT_CIRCULATION_VIEW);
                if (hintedRecipient != null || manager) {
                    distributionId = distributionBatchHint;
                    recipient = hintedRecipient;
                }
            }
        }
        if (distributionId == null) {
            for (DocumentDistributionItem issuedItem : issued) {
                DocumentDistributionRecipient candidate = recipientForUser(issuedItem.getBatchId(), user.getId());
                if (candidate != null) {
                    distributionId = issuedItem.getBatchId();
                    recipient = candidate;
                    break;
                }
            }
        }
        recordEvent(document.getProjectId(), null, distributionId,
                recipient == null ? null : recipient.getId(), document.getId(), version.getId(), user,
                preview ? "PREVIEW" : "DOWNLOAD", "ELECTRONIC", success ? "SUCCESS" : "FAILED",
                (preview ? "预览" : "下载") + "精确资料版本V" + version.getVersionNo(), null, request);
    }

    @Transactional
    public int sendOverdueReminders(int limit) {
        List<DocumentDistributionBatch> candidates = distributionBatchMapper.selectList(
                new LambdaQueryWrapper<DocumentDistributionBatch>()
                        .in(DocumentDistributionBatch::getStatus, List.of("PUBLISHED", "DISPUTED"))
                        .lt(DocumentDistributionBatch::getDeadline, now())
                        .isNull(DocumentDistributionBatch::getOverdueNotificationTime)
                        .orderByAsc(DocumentDistributionBatch::getDeadline)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
        int processed = 0;
        for (DocumentDistributionBatch candidate : candidates) {
            DocumentDistributionBatch batch = distributionBatchMapper.selectForUpdate(candidate.getId());
            if (batch == null || batch.getOverdueNotificationTime() != null
                    || Set.of("COMPLETED", "VOIDED").contains(batch.getStatus())
                    || !batch.getDeadline().isBefore(now())) continue;
            List<DocumentDistributionRecipient> pending = recipientMapper.selectList(
                    new LambdaQueryWrapper<DocumentDistributionRecipient>()
                            .eq(DocumentDistributionRecipient::getBatchId, batch.getId())
                            .eq(DocumentDistributionRecipient::getStatus, "PENDING"));
            for (DocumentDistributionRecipient recipient : pending) {
                notificationService.notify(recipient.getUserId(), batch.getProjectId(), BUSINESS_TYPE, batch.getId(),
                        "DOCUMENT_DISTRIBUTION_OVERDUE", "图纸签收已逾期",
                        batch.getDistributionNo() + "已超过签收期限，请尽快处理",
                        "document-distribution-overdue:recipient:" + recipient.getId(), ROUTE_CODE,
                        routeParams(batch.getId()));
                recipient.setReminderSentTime(now());
                recipient.setUpdateTime(now());
                requireSingleWrite(recipientMapper.updateById(recipient), "接收人逾期提醒状态更新");
            }
            notificationService.notify(batch.getPublishedBy(), batch.getProjectId(), BUSINESS_TYPE, batch.getId(),
                    "DOCUMENT_DISTRIBUTION_OVERDUE_TECHNICIAN", "图纸发放存在逾期未签收",
                    batch.getDistributionNo() + "仍有" + pending.size() + "人未签收",
                    "document-distribution-overdue:publisher:" + batch.getId(), ROUTE_CODE,
                    routeParams(batch.getId()));
            batch.setOverdueNotificationTime(now());
            batch.setVersion(batch.getVersion() + 1);
            batch.setUpdateTime(now());
            requireSingleWrite(distributionBatchMapper.updateById(batch), "发放批次逾期提醒状态更新");
            recordEvent(batch.getProjectId(), batch.getIncomingBatchId(), batch.getId(), null, null, null,
                    null, "OVERDUE_REMINDER", null, "SUCCESS", "已生成一次逾期站内提醒",
                    Map.of("pendingRecipientCount", pending.size()), null);
            processed++;
        }
        return processed;
    }

    private PublishedItem publishIncomingItem(DocumentIncomingBatch batch, DocumentIncomingItem item, SysUser user) {
        FileResource file = fileMapper.selectById(item.getFileResourceId());
        if (file == null || !batch.getProjectId().equals(file.getProjectId())
                || !"DOCUMENT_INCOMING_PENDING".equals(file.getBusinessType())
                || !batch.getId().equals(file.getBusinessId()) || !FileStatus.UPLOADED.equals(file.getStatus())) {
            throw BusinessException.of(409, "收文文件暂存状态已变化，请刷新后重试");
        }
        ProjectDocument document;
        ProjectDocumentVersion previous = null;
        int versionNo;
        if ("NEW_DOCUMENT".equals(item.getMatchMode())) {
            ensureIdentityAvailable(batch.getProjectId(), item.getDocumentType(), item.getDocumentNo(), null);
            document = new ProjectDocument();
            document.setProjectId(batch.getProjectId());
            document.setFolderId(item.getFolderId() == null ? 0L : item.getFolderId());
            document.setDocumentNo(item.getDocumentNo());
            document.setTitle(item.getTitle());
            document.setCategory("DRAWING".equals(item.getDocumentType()) ? "DRAWING" : "PROJECT_DATA");
            document.setDocumentType(item.getDocumentType());
            document.setStatus(ProjectDocumentService.STATUS_ACTIVE);
            document.setCreatedBy(user.getId());
            document.setCreatedByName(displayName(user));
            document.setRemark("由收文批次" + batch.getIncomingNo() + "发布");
            document.setDeleted(0);
            document.setCreateTime(now());
            document.setUpdateTime(now());
            requireSingleWrite(documentMapper.insert(document), "正式资料新增");
            versionNo = 1;
        } else {
            if (item.getTargetDocumentId() == null) throw new BusinessException("已有资料新版本必须选择目标资料");
            document = documentMapper.selectForUpdate(item.getTargetDocumentId());
            if (document == null || !batch.getProjectId().equals(document.getProjectId())) {
                throw BusinessException.notFound("匹配的目标资料不存在");
            }
            if (!item.getDocumentType().equals(document.getDocumentType())) {
                throw new BusinessException("新版本正式类型与目标资料不一致");
            }
            if (!normalizedIdentity(item.getDocumentNo()).equals(normalizedIdentity(document.getDocumentNo()))) {
                throw new BusinessException("新版本图号或编号与目标资料不一致");
            }
            previous = versionMapper.selectById(document.getCurrentVersionId());
            if (previous == null) throw BusinessException.of(409, "目标资料当前版本不存在");
            if (StringUtils.hasText(item.getExternalRevision())
                    && externalRevisionAlreadyUsed(document.getId(), item.getExternalRevision())
                    && !StringUtils.hasText(item.getDuplicateRevisionReason())) {
                throw new BusinessException("外部版次重复时必须填写原因");
            }
            versionNo = versionMapper.selectMaxVersionNo(document.getId()) + 1;
        }
        ProjectDocumentVersion version = new ProjectDocumentVersion();
        version.setDocumentId(document.getId());
        version.setVersionNo(versionNo);
        version.setFileResourceId(file.getId());
        version.setChangeNote(item.getChangeNote());
        version.setExternalRevision(item.getExternalRevision());
        version.setVersionStatus("CURRENT");
        version.setPublishedBy(user.getId());
        version.setPublishedByName(displayName(user));
        version.setPublishedTime(now());
        version.setCreatedBy(user.getId());
        version.setCreatedByName(displayName(user));
        version.setCreateTime(now());
        requireSingleWrite(versionMapper.insert(version), "正式资料版本新增");
        if (previous != null) {
            requireSingleWrite(versionMapper.markSuperseded(previous.getId(), version.getId()), "旧版本替代");
            recordEvent(batch.getProjectId(), batch.getId(), null, null, document.getId(), previous.getId(),
                    user, "VERSION_SUPERSEDED", null, "SUCCESS", "V" + previous.getVersionNo()
                            + "已由V" + versionNo + "替代", Map.of("newVersionId", version.getId()), null);
        }
        document.setCurrentVersionId(version.getId());
        document.setTitle(item.getTitle());
        document.setUpdateTime(now());
        requireSingleWrite(documentMapper.updateById(document), "正式资料当前版本切换");
        requireSingleWrite(fileMapper.bindDocumentIncomingFile(file.getId(), batch.getProjectId(),
                batch.getId(), document.getId()), "收文文件正式绑定");
        item.setPublishedDocumentId(document.getId());
        item.setPublishedVersionId(version.getId());
        item.setStatus("PUBLISHED");
        item.setUpdateTime(now());
        requireSingleWrite(incomingItemMapper.updateById(item), "收文文件发布状态更新");
        return new PublishedItem(item.getId(), document, version, previous == null ? null : previous.getId());
    }

    private DocumentDistributionBatch createDistribution(Long projectId, Long incomingBatchId,
                                                          List<PublishedItem> published,
                                                          DocumentDistributionSettingsRequest settings,
                                                          Set<Long> mandatoryUsers,
                                                          SysUser publisher,
                                                          HttpServletRequest request) {
        validateDistributionSettings(settings);
        if (published.isEmpty()) throw new BusinessException("发放批次至少包含一份资料版本");
        Map<Long, ProjectMemberVO> activeMembers = activeDocumentMembers(projectId);
        LinkedHashMap<Long, DocumentRecipientRequest> selected = new LinkedHashMap<>();
        for (DocumentRecipientRequest recipient : settings.getRecipients()) {
            if (selected.putIfAbsent(recipient.getUserId(), recipient) != null) {
                throw new BusinessException("接收人不能重复选择");
            }
        }
        for (Long mandatory : mandatoryUsers) {
            if (!selected.containsKey(mandatory)) {
                throw new BusinessException("旧版本历史使用人属于强制接收人，不能从本次发放名单删除");
            }
        }
        if (selected.isEmpty()) throw new BusinessException("至少选择一名接收人");
        for (Long userId : selected.keySet()) {
            if (!activeMembers.containsKey(userId)) {
                throw new BusinessException("接收人不是当前项目有效且具备资料查看权限的成员");
            }
        }
        LocalDateTime now = now();
        String scene = sceneService.newScene();
        DocumentDistributionBatch batch = new DocumentDistributionBatch();
        batch.setProjectId(projectId);
        batch.setIncomingBatchId(incomingBatchId);
        batch.setDistributionNo(newNumber("FF"));
        batch.setDeadline(settings.getDeadline());
        batch.setNotificationTemplate(defaultNotificationContent(published.size(), settings.getDeadline()));
        batch.setMessageNote(optionalText(settings.getMessageNote(), 500, "补充说明"));
        batch.setElectronicSignatureRequired(Boolean.TRUE.equals(settings.getElectronicSignatureRequired()) ? 1 : 0);
        batch.setPaperSignatureRequired(Boolean.FALSE.equals(settings.getPaperSignatureRequired()) ? 0 : 1);
        batch.setQrSceneDigest(sceneService.digest(scene));
        batch.setQrSceneCiphertext(sceneService.encrypt(scene));
        batch.setQrStatus("ACTIVE");
        batch.setStatus("PUBLISHED");
        batch.setPublishedBy(publisher.getId());
        batch.setPublishedByName(displayName(publisher));
        batch.setPublishedTime(now);
        batch.setVersion(0);
        batch.setCreateTime(now);
        batch.setUpdateTime(now);
        requireSingleWrite(distributionBatchMapper.insert(batch), "发放批次新增");

        Map<Long, DocumentDistributionItem> distributionItems = new LinkedHashMap<>();
        int order = 1;
        for (PublishedItem value : published) {
            FileResource file = fileMapper.selectById(value.version().getFileResourceId());
            if (file == null) throw BusinessException.of(409, "资料版本文件不存在");
            DocumentDistributionItem item = new DocumentDistributionItem();
            item.setBatchId(batch.getId());
            item.setProjectId(projectId);
            item.setItemOrder(order++);
            item.setDocumentId(value.document().getId());
            item.setVersionId(value.version().getId());
            item.setDocumentNoSnapshot(value.document().getDocumentNo());
            item.setTitleSnapshot(value.document().getTitle());
            item.setDocumentTypeSnapshot(value.document().getDocumentType());
            item.setSystemVersionNo(value.version().getVersionNo());
            item.setExternalRevisionSnapshot(value.version().getExternalRevision());
            item.setFileNameSnapshot(StringUtils.hasText(file.getOriginalFileName())
                    ? file.getOriginalFileName() : file.getFileName());
            item.setSha256Snapshot(file.getSha256());
            item.setCreateTime(now);
            requireSingleWrite(distributionItemMapper.insert(item), "发放版本快照新增");
            distributionItems.put(value.incomingItemId() == null ? value.version().getId() : value.incomingItemId(), item);
        }
        for (DocumentRecipientRequest selection : selected.values()) {
            ProjectMemberVO member = activeMembers.get(selection.getUserId());
            String channel = normalizeChannel(selection.getChannel());
            DocumentDistributionRecipient recipient = new DocumentDistributionRecipient();
            recipient.setBatchId(batch.getId());
            recipient.setProjectId(projectId);
            recipient.setUserId(member.getUserId());
            recipient.setUsernameSnapshot(member.getUsername());
            recipient.setRealNameSnapshot(displayName(member));
            recipient.setPhoneSnapshot(member.getPhone());
            recipient.setRoleNamesSnapshot(roleNames(member.getUserId(), projectId));
            recipient.setMemberStatusSnapshot(member.getAccessStatus());
            recipient.setChannel(channel);
            recipient.setStatus("PENDING");
            recipient.setMandatory(mandatoryUsers.contains(member.getUserId()) ? 1 : 0);
            recipient.setNotifiedTime(now);
            recipient.setCreateTime(now);
            recipient.setUpdateTime(now);
            requireSingleWrite(recipientMapper.insert(recipient), "发放接收人新增");
            Map<Long, DocumentRecipientCopyRequest> copies = copyRequestByKey(selection.getCopies());
            for (Map.Entry<Long, DocumentDistributionItem> entry : distributionItems.entrySet()) {
                DocumentRecipientCopyRequest requested = copies.get(entry.getKey());
                int count = requested == null || requested.getPaperCopyCount() == null
                        ? ("ELECTRONIC".equals(channel) ? 0 : 1) : requested.getPaperCopyCount();
                if ("ELECTRONIC".equals(channel) && count != 0) {
                    throw new BusinessException("电子签收不能填写纸质领取份数");
                }
                if (!"ELECTRONIC".equals(channel) && count < 1) {
                    throw new BusinessException("纸质领取的每份文件至少填写1份");
                }
                DocumentDistributionRecipientItem recipientItem = new DocumentDistributionRecipientItem();
                recipientItem.setBatchId(batch.getId());
                recipientItem.setRecipientId(recipient.getId());
                recipientItem.setDistributionItemId(entry.getValue().getId());
                recipientItem.setPaperCopyCount(count);
                recipientItem.setCreateTime(now);
                requireSingleWrite(recipientItemMapper.insert(recipientItem), "接收人逐文件份数新增");
            }
            notificationService.notify(member.getUserId(), projectId, BUSINESS_TYPE, batch.getId(),
                    "DOCUMENT_DISTRIBUTION_PUBLISHED", "待签收图纸资料",
                    batch.getDistributionNo() + "：" + batch.getNotificationTemplate(),
                    "document-distribution-published:" + recipient.getId(), ROUTE_CODE, routeParams(batch.getId()));
            recordEvent(projectId, incomingBatchId, batch.getId(), recipient.getId(), null, null,
                    publisher, "NOTIFICATION_CREATED", channel, "SUCCESS", "已生成站内通知和个人待办",
                    Map.of("recipientUserId", member.getUserId()), request);
        }
        recordEvent(projectId, incomingBatchId, batch.getId(), null, null, null, publisher,
                "DISTRIBUTION_PUBLISHED", null, "SUCCESS", "发放批次已发布",
                Map.of("itemCount", published.size(), "recipientCount", selected.size()), request);
        return batch;
    }

    private Map<Long, DocumentRecipientCopyRequest> copyRequestByKey(List<DocumentRecipientCopyRequest> copies) {
        Map<Long, DocumentRecipientCopyRequest> result = new HashMap<>();
        if (copies == null) return result;
        for (DocumentRecipientCopyRequest copy : copies) {
            Long key = copy.getIncomingItemId() != null ? copy.getIncomingItemId() : copy.getVersionId();
            if (key == null || result.putIfAbsent(key, copy) != null) {
                throw new BusinessException("接收人逐文件份数配置重复或缺少文件标识");
            }
        }
        return result;
    }

    private void replaceDraftItems(DocumentIncomingBatch batch, List<DocumentIncomingItemRequest> requests, SysUser user) {
        incomingItemMapper.deleteDraftItems(batch.getId());
        int order = 1;
        for (DocumentIncomingItemRequest request : requests == null ? List.<DocumentIncomingItemRequest>of() : requests) {
            FileResource file = fileMapper.selectById(request.getFileResourceId());
            if (file == null || !batch.getProjectId().equals(file.getProjectId())
                    || !"DOCUMENT_INCOMING_PENDING".equals(file.getBusinessType())
                    || !batch.getId().equals(file.getBusinessId())
                    || !user.getId().equals(file.getUploaderId()) || file.getDeleted() != 0) {
                throw new BusinessException("只能使用本人上传到当前收文草稿的待发布文件");
            }
            DocumentIncomingItem item = new DocumentIncomingItem();
            String type = normalizeFormalType(request.getDocumentType());
            String matchMode = normalize(request.getMatchMode());
            if (!MATCH_MODES.contains(matchMode)) throw new BusinessException("版本匹配方式不正确");
            String documentNo = optionalText(request.getDocumentNo(), 100, "图号或编号");
            if ("DRAWING".equals(type) && !StringUtils.hasText(documentNo)) throw new BusinessException("图纸必须填写图号");
            if ("TECHNICAL_DOCUMENT".equals(type) && !StringUtils.hasText(documentNo)) {
                documentNo = technicalNumber(batch, order);
            }
            if ("NEW_VERSION".equals(matchMode) && request.getTargetDocumentId() == null) {
                throw new BusinessException("已有资料新版本必须选择目标资料");
            }
            if ("NEW_DOCUMENT".equals(matchMode) && request.getTargetDocumentId() != null) {
                throw new BusinessException("新资料不能指定已有资料");
            }
            item.setBatchId(batch.getId());
            item.setProjectId(batch.getProjectId());
            item.setCreateTime(now());
            item.setFileResourceId(request.getFileResourceId());
            item.setFolderId(request.getFolderId() == null ? 0L : request.getFolderId());
            item.setItemOrder(order++);
            item.setTitle(requiredText(request.getTitle(), 200, "文件标题"));
            item.setDocumentNo(documentNo);
            item.setDocumentType(type);
            item.setExternalRevision(optionalText(request.getExternalRevision(), 100, "外部版次"));
            item.setMatchMode(matchMode);
            item.setTargetDocumentId(request.getTargetDocumentId());
            item.setDuplicateRevisionReason(optionalText(request.getDuplicateRevisionReason(), 500, "重复版次原因"));
            item.setChangeNote(optionalText(request.getChangeNote(), 500, "版本说明"));
            item.setStatus("PENDING");
            item.setUpdateTime(now());
            requireSingleWrite(incomingItemMapper.insert(item), "收文核对项新增");
        }
    }

    private Map<Long, ProjectMemberVO> activeDocumentMembers(Long projectId) {
        Map<Long, ProjectMemberVO> result = new LinkedHashMap<>();
        for (ProjectMemberVO member : userProjectMapper.selectMembersByProjectId(projectId)) {
            if (!"ACTIVE".equals(member.getAccessStatus()) || !Integer.valueOf(1).equals(member.getStatus())) continue;
            if (!permissionService.hasSystemPermission(member.getUserId(), projectId, SystemPermissionCodes.DOCUMENT_VIEW)) continue;
            result.put(member.getUserId(), member);
        }
        return result;
    }

    private Set<Long> mandatoryRecipientUsers(Iterable<Long> previousVersionIds, Long projectId) {
        Set<Long> candidates = historicalRecipientUsers(previousVersionIds);
        candidates.retainAll(activeDocumentMembers(projectId).keySet());
        return candidates;
    }

    private Set<Long> excludedHistoricalRecipientUsers(Iterable<Long> previousVersionIds, Long projectId) {
        Set<Long> historical = historicalRecipientUsers(previousVersionIds);
        historical.removeAll(activeDocumentMembers(projectId).keySet());
        return historical;
    }

    private Set<Long> historicalRecipientUsers(Iterable<Long> versionIds) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Long versionId : versionIds) {
            if (versionId == null) continue;
            eventMapper.selectList(new LambdaQueryWrapper<DocumentCirculationEvent>()
                            .eq(DocumentCirculationEvent::getVersionId, versionId)
                            .in(DocumentCirculationEvent::getEventType, List.of("DOWNLOAD", "CONFIRM"))
                            .isNotNull(DocumentCirculationEvent::getUserId))
                    .forEach(event -> ids.add(event.getUserId()));
        }
        return ids;
    }

    boolean externalRevisionAlreadyUsed(Long documentId, String externalRevision) {
        String normalized = externalRevision.trim();
        return versionMapper.selectList(new LambdaQueryWrapper<ProjectDocumentVersion>()
                        .eq(ProjectDocumentVersion::getDocumentId, documentId))
                .stream().map(ProjectDocumentVersion::getExternalRevision)
                .filter(StringUtils::hasText)
                .anyMatch(existing -> normalized.equalsIgnoreCase(existing.trim()));
    }

    private DocumentDistributionRecipient recipientForUser(Long batchId, Long userId) {
        return recipientMapper.selectOne(new LambdaQueryWrapper<DocumentDistributionRecipient>()
                .eq(DocumentDistributionRecipient::getBatchId, batchId)
                .eq(DocumentDistributionRecipient::getUserId, userId)
                .last("LIMIT 1"));
    }

    private void requireRecipientAccess(DocumentDistributionBatch batch, DocumentDistributionRecipient recipient,
                                        SysUser user) {
        if (user == null || user.getId() == null) throw BusinessException.unauthorized("请先登录");
        if (recipient == null || !user.getId().equals(recipient.getUserId())) {
            throw BusinessException.forbidden("您不在该发放批次接收名单内");
        }
        if (!"ACTIVE".equals(permissionService.getProjectAccessStatus(user.getId(), batch.getProjectId()))
                || !permissionService.hasSystemPermission(user.getId(), batch.getProjectId(), SystemPermissionCodes.DOCUMENT_VIEW)) {
            throw BusinessException.forbidden("当前项目成员或资料访问权限已失效");
        }
        if ("VOIDED".equals(batch.getStatus()) || !"ACTIVE".equals(batch.getQrStatus())) {
            throw BusinessException.of(409, "发放批次已作废");
        }
    }

    private void verifyScene(DocumentDistributionBatch batch, String rawScene) {
        if (!StringUtils.hasText(rawScene)) throw new BusinessException("纸质领取必须扫描批次二维码");
        String normalized = sceneService.normalizeScene(rawScene);
        if (!sceneService.digest(normalized).equals(batch.getQrSceneDigest())) {
            throw BusinessException.forbidden("图纸领取二维码与当前批次不匹配");
        }
    }

    private Long storeSignature(DocumentDistributionBatch batch, DocumentDistributionRecipient recipient,
                                MultipartFile signature, SysUser user) {
        String key = "document-receipts/" + batch.getProjectId() + "/" + batch.getId() + "/"
                + UUID.randomUUID() + ".png";
        StoredFile stored = storageManager.store(key, signature);
        registerRollbackCleanup(stored);
        FileResource file = new FileResource();
        file.setProjectId(batch.getProjectId());
        file.setFileName("图纸签收-" + recipient.getRealNameSnapshot() + ".png");
        file.setFileType("DOCUMENT_RECEIPT_SIGNATURE");
        file.setFilePath(stored.storageKey());
        file.setFileSize(stored.size());
        file.setBusinessType("DOCUMENT_RECEIPT_SIGNATURE");
        file.setBusinessId(recipient.getId());
        file.setUploaderId(user.getId());
        file.setStorageProvider(stored.provider());
        file.setStorageKey(stored.storageKey());
        file.setOriginalFileName(stored.originalFileName());
        file.setMimeType(stored.mimeType());
        file.setFileExtension(stored.extension());
        file.setSha256(stored.sha256());
        file.setStatus(FileStatus.UPLOADED);
        file.setDeleted(0);
        file.setCreateTime(now());
        file.setUpdateTime(now());
        requireSingleWrite(fileMapper.insert(file), "签名附件新增");
        return file.getId();
    }

    private DocumentDistributionBatchVO toDistributionVO(DocumentDistributionBatch batch,
                                                           DocumentDistributionRecipient current,
                                                           boolean includeRecipients) {
        DocumentDistributionBatchVO vo = new DocumentDistributionBatchVO();
        vo.setId(batch.getId());
        vo.setProjectId(batch.getProjectId());
        vo.setIncomingBatchId(batch.getIncomingBatchId());
        vo.setDistributionNo(batch.getDistributionNo());
        vo.setDeadline(batch.getDeadline());
        vo.setNotificationTemplate(batch.getNotificationTemplate());
        vo.setMessageNote(batch.getMessageNote());
        vo.setElectronicSignatureRequired(truth(batch.getElectronicSignatureRequired()));
        vo.setPaperSignatureRequired(truth(batch.getPaperSignatureRequired()));
        vo.setQrStatus(batch.getQrStatus());
        vo.setStatus(batch.getStatus());
        vo.setPublishedBy(batch.getPublishedBy());
        vo.setPublishedByName(batch.getPublishedByName());
        vo.setPublishedTime(batch.getPublishedTime());
        vo.setOverdueNotificationTime(batch.getOverdueNotificationTime());
        vo.setVoidReason(batch.getVoidReason());
        vo.setOverdue(batch.getDeadline() != null && batch.getDeadline().isBefore(now())
                && !Set.of("COMPLETED", "VOIDED").contains(batch.getStatus()));
        List<DocumentDistributionItem> items = distributionItems(batch.getId());
        vo.setItems(items.stream().map(item -> toDistributionItemVO(item,
                current == null ? null : paperCopyCount(current.getId(), item.getId()))).toList());
        if (includeRecipients) {
            vo.setRecipients(recipientMapper.selectList(new LambdaQueryWrapper<DocumentDistributionRecipient>()
                            .eq(DocumentDistributionRecipient::getBatchId, batch.getId()).orderByAsc(DocumentDistributionRecipient::getId))
                    .stream().map(recipient -> toRecipientVO(recipient, items)).toList());
        }
        if (current != null) {
            vo.setCurrentRecipient(toRecipientVO(current, items));
            boolean signature = "ELECTRONIC".equals(current.getChannel())
                    ? truth(batch.getElectronicSignatureRequired())
                    : truth(batch.getPaperSignatureRequired()) || ("BOTH".equals(current.getChannel())
                    && truth(batch.getElectronicSignatureRequired()));
            vo.setSignatureRequiredForCurrentRecipient(signature);
            vo.setScanRequiredForCurrentRecipient(!"ELECTRONIC".equals(current.getChannel()));
        }
        return vo;
    }

    private DocumentDistributionRecipientVO toRecipientVO(DocumentDistributionRecipient recipient,
                                                           List<DocumentDistributionItem> items) {
        DocumentDistributionRecipientVO vo = new DocumentDistributionRecipientVO();
        vo.setId(recipient.getId());
        vo.setUserId(recipient.getUserId());
        vo.setUsername(recipient.getUsernameSnapshot());
        vo.setRealName(recipient.getRealNameSnapshot());
        vo.setPhone(recipient.getPhoneSnapshot());
        vo.setRoleNames(recipient.getRoleNamesSnapshot());
        vo.setMemberStatus(recipient.getMemberStatusSnapshot());
        vo.setChannel(recipient.getChannel());
        vo.setStatus(recipient.getStatus());
        vo.setMandatory(truth(recipient.getMandatory()));
        vo.setNotifiedTime(recipient.getNotifiedTime());
        vo.setReminderSentTime(recipient.getReminderSentTime());
        vo.setConfirmedTime(recipient.getConfirmedTime());
        vo.setSignatureFileId(recipient.getSignatureFileId());
        vo.setDisputeNote(recipient.getDisputeNote());
        vo.setDisputeTime(recipient.getDisputeTime());
        vo.setItems(items.stream().map(item -> toDistributionItemVO(item,
                paperCopyCount(recipient.getId(), item.getId()))).toList());
        return vo;
    }

    private DocumentDistributionItemVO toDistributionItemVO(DocumentDistributionItem item, Integer copies) {
        DocumentDistributionItemVO vo = new DocumentDistributionItemVO();
        vo.setId(item.getId());
        vo.setItemOrder(item.getItemOrder());
        vo.setDocumentId(item.getDocumentId());
        vo.setVersionId(item.getVersionId());
        vo.setDocumentNo(item.getDocumentNoSnapshot());
        vo.setTitle(item.getTitleSnapshot());
        vo.setDocumentType(item.getDocumentTypeSnapshot());
        vo.setSystemVersionNo(item.getSystemVersionNo());
        vo.setExternalRevision(item.getExternalRevisionSnapshot());
        vo.setFileName(item.getFileNameSnapshot());
        vo.setSha256(item.getSha256Snapshot());
        vo.setPaperCopyCount(copies);
        return vo;
    }

    private DocumentIncomingBatchVO toIncomingSummary(DocumentIncomingBatch batch) {
        DocumentIncomingBatchVO vo = new DocumentIncomingBatchVO();
        vo.setId(batch.getId());
        vo.setProjectId(batch.getProjectId());
        vo.setIncomingNo(batch.getIncomingNo());
        vo.setSourceOrganization(batch.getSourceOrganization());
        vo.setSenderName(batch.getSenderName());
        vo.setSourceReferenceNo(batch.getSourceReferenceNo());
        vo.setReceiveMethod(batch.getReceiveMethod());
        vo.setReceivedAt(batch.getReceivedAt());
        vo.setReceiverId(batch.getReceiverId());
        vo.setReceiverName(batch.getReceiverName());
        vo.setRemark(batch.getRemark());
        vo.setStatus(batch.getStatus());
        vo.setPublishedBy(batch.getPublishedBy());
        vo.setPublishedByName(batch.getPublishedByName());
        vo.setPublishedTime(batch.getPublishedTime());
        vo.setVoidReason(batch.getVoidReason());
        vo.setVersion(batch.getVersion());
        vo.setCreateTime(batch.getCreateTime());
        vo.setUpdateTime(batch.getUpdateTime());
        return vo;
    }

    private DocumentIncomingItemVO toIncomingItemVO(DocumentIncomingItem item) {
        DocumentIncomingItemVO vo = new DocumentIncomingItemVO();
        vo.setId(item.getId());
        vo.setFileResourceId(item.getFileResourceId());
        vo.setFolderId(item.getFolderId());
        vo.setItemOrder(item.getItemOrder());
        vo.setTitle(item.getTitle());
        vo.setDocumentNo(item.getDocumentNo());
        vo.setDocumentType(item.getDocumentType());
        vo.setExternalRevision(item.getExternalRevision());
        vo.setMatchMode(item.getMatchMode());
        vo.setTargetDocumentId(item.getTargetDocumentId());
        vo.setDuplicateRevisionReason(item.getDuplicateRevisionReason());
        vo.setChangeNote(item.getChangeNote());
        vo.setPublishedDocumentId(item.getPublishedDocumentId());
        vo.setPublishedVersionId(item.getPublishedVersionId());
        vo.setStatus(item.getStatus());
        vo.setCreateTime(item.getCreateTime());
        FileResource file = fileMapper.selectById(item.getFileResourceId());
        if (file != null) {
            vo.setFileName(StringUtils.hasText(file.getOriginalFileName()) ? file.getOriginalFileName() : file.getFileName());
            vo.setFileSize(file.getFileSize());
            vo.setSha256(file.getSha256());
        }
        return vo;
    }

    private void copyIncomingFields(DocumentIncomingBatch batch, DocumentIncomingBatchSaveRequest request) {
        batch.setSourceOrganization(requiredText(request.getSourceOrganization(), 200, "来源单位"));
        batch.setSenderName(optionalText(request.getSenderName(), 100, "发件人"));
        batch.setSourceReferenceNo(optionalText(request.getSourceReferenceNo(), 100, "来文编号"));
        batch.setReceiveMethod(optionalText(request.getReceiveMethod(), 30, "接收方式"));
        if (request.getReceivedAt() == null) throw new BusinessException("收文时间不能为空");
        batch.setReceivedAt(request.getReceivedAt());
        batch.setRemark(optionalText(request.getRemark(), 500, "备注"));
    }

    private void validateDistributionSettings(DocumentDistributionSettingsRequest settings) {
        if (settings == null) throw new BusinessException("发放设置不能为空");
        if (settings.getDeadline() == null || !settings.getDeadline().isAfter(now())) {
            throw new BusinessException("签收期限必须晚于当前时间");
        }
        if (settings.getRecipients() == null || settings.getRecipients().isEmpty()) {
            throw new BusinessException("至少选择一名接收人");
        }
        for (DocumentRecipientRequest recipient : settings.getRecipients()) {
            if (recipient.getUserId() == null) throw new BusinessException("接收人不能为空");
            normalizeChannel(recipient.getChannel());
        }
    }

    private void ensureIdentityAvailable(Long projectId, String type, String documentNo, Long excludeId) {
        String identity = normalizedIdentity(documentNo);
        if (!StringUtils.hasText(identity)) throw new BusinessException("图号或编号不能为空");
        boolean duplicate = documentMapper.selectList(new LambdaQueryWrapper<ProjectDocument>()
                        .eq(ProjectDocument::getProjectId, projectId)
                        .eq(ProjectDocument::getDocumentType, type))
                .stream().anyMatch(document -> !Objects.equals(document.getId(), excludeId)
                        && identity.equals(normalizedIdentity(document.getDocumentNo())));
        if (duplicate) throw BusinessException.of(409, "当前项目已存在相同类型和图号/编号的资料");
    }

    private List<DocumentIncomingItem> incomingItems(Long batchId) {
        return incomingItemMapper.selectList(new LambdaQueryWrapper<DocumentIncomingItem>()
                .eq(DocumentIncomingItem::getBatchId, batchId)
                .orderByAsc(DocumentIncomingItem::getItemOrder));
    }

    private List<DocumentDistributionItem> distributionItems(Long batchId) {
        return distributionItemMapper.selectList(new LambdaQueryWrapper<DocumentDistributionItem>()
                .eq(DocumentDistributionItem::getBatchId, batchId)
                .orderByAsc(DocumentDistributionItem::getItemOrder));
    }

    private int paperCopyCount(Long recipientId, Long distributionItemId) {
        DocumentDistributionRecipientItem item = recipientItemMapper.selectOne(
                new LambdaQueryWrapper<DocumentDistributionRecipientItem>()
                        .eq(DocumentDistributionRecipientItem::getRecipientId, recipientId)
                        .eq(DocumentDistributionRecipientItem::getDistributionItemId, distributionItemId)
                        .last("LIMIT 1"));
        return item == null || item.getPaperCopyCount() == null ? 0 : item.getPaperCopyCount();
    }

    private DocumentIncomingBatch requireIncoming(Long id) {
        DocumentIncomingBatch batch = incomingBatchMapper.selectById(id);
        if (batch == null) throw BusinessException.notFound("收文批次不存在");
        return batch;
    }

    private DocumentDistributionBatch requireDistribution(Long id) {
        DocumentDistributionBatch batch = distributionBatchMapper.selectById(id);
        if (batch == null) throw BusinessException.notFound("发放批次不存在");
        return batch;
    }

    private void requireDraft(DocumentIncomingBatch batch, Long projectId) {
        if (batch == null || !Objects.equals(projectId, batch.getProjectId())) throw BusinessException.notFound("收文草稿不存在");
        if (!"DRAFT".equals(batch.getStatus())) throw BusinessException.of(409, "收文批次已发布或作废，不能修改");
    }

    private void requireAnyCirculationRead(SysUser user, Long projectId) {
        if (permissionService.hasSystemPermission(user.getId(), projectId, SystemPermissionCodes.DOCUMENT_CIRCULATION_VIEW)
                || permissionService.hasSystemPermission(user.getId(), projectId, SystemPermissionCodes.DOCUMENT_RECEIVE)) {
            permissionService.checkProjectPermission(user.getId(), projectId);
            return;
        }
        throw BusinessException.forbidden("无图纸收发查看权限");
    }

    private void requirePermission(SysUser user, Long projectId, String permission) {
        if (user == null || user.getId() == null) throw BusinessException.unauthorized("请先登录");
        permissionService.checkProjectPermission(user.getId(), projectId);
        permissionService.requireSystemPermission(user.getId(), projectId, permission);
    }

    private String roleNames(Long userId, Long projectId) {
        return userProjectRoleMapper.selectEnabledRoles(userId, projectId).stream()
                .map(SystemRole::getRoleName).filter(StringUtils::hasText).distinct().collect(Collectors.joining("、"));
    }

    private String displayName(SysUser user) {
        if (user == null) return "未知用户";
        return StringUtils.hasText(user.getRealName()) ? user.getRealName().trim() : user.getUsername();
    }

    private String displayName(ProjectMemberVO member) {
        return StringUtils.hasText(member.getRealName()) ? member.getRealName().trim() : member.getUsername();
    }

    private String technicalNumber(DocumentIncomingBatch batch, int order) {
        return "TECH-" + batch.getProjectId() + "-" + batch.getId() + "-" + String.format(Locale.ROOT, "%03d", order);
    }

    private String defaultNotificationContent(int itemCount, LocalDateTime deadline) {
        return "您有" + itemCount + "份图纸或技术文件待签收，请于"
                + deadline.format(NOTIFICATION_TIME) + "前完成确认。";
    }

    private String newNumber(String prefix) {
        return prefix + LocalDateTime.now(BUSINESS_ZONE).format(NUMBER_TIME) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String routeParams(Long batchId) {
        return "{\"distributionId\":" + batchId + "}";
    }

    private String normalizeFormalType(String value) {
        String normalized = normalize(value);
        if (!FORMAL_TYPES.contains(normalized)) throw new BusinessException("正式类型必须为图纸或技术文件");
        return normalized;
    }

    private String normalizeChannel(String value) {
        String normalized = normalize(value);
        if (!CHANNELS.contains(normalized)) throw new BusinessException("接收渠道不正确");
        return normalized;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String normalizedIdentity(String value) {
        return StringUtils.hasText(value) ? value.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT) : "";
    }

    private String requiredText(String value, int max, String label) {
        String normalized = optionalText(value, max, label);
        if (!StringUtils.hasText(normalized)) throw new BusinessException(label + "不能为空");
        return normalized;
    }

    private String optionalText(String value, int max, String label) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new BusinessException(label + "不能超过" + max + "个字符");
        return normalized;
    }

    private boolean truth(Integer value) {
        return Integer.valueOf(1).equals(value);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }

    private void requireSingleWrite(int rows, String action) {
        if (rows != 1) throw BusinessException.of(409, action + "未生效，请刷新后重试");
    }

    private void recordEvent(Long projectId, Long incomingBatchId, Long distributionBatchId,
                             Long recipientId, Long documentId, Long versionId, SysUser user,
                             String eventType, String channel, String result, String summary,
                             Object data, HttpServletRequest request) {
        DocumentCirculationEvent event = new DocumentCirculationEvent();
        event.setProjectId(projectId);
        event.setIncomingBatchId(incomingBatchId);
        event.setDistributionBatchId(distributionBatchId);
        event.setRecipientId(recipientId);
        event.setDocumentId(documentId);
        event.setVersionId(versionId);
        event.setUserId(user == null ? null : user.getId());
        event.setUserName(user == null ? null : displayName(user));
        event.setEventType(eventType);
        event.setChannel(channel);
        event.setEventResult(result);
        event.setEventSummary(summary);
        if (data != null) {
            try {
                event.setEventDataJson(objectMapper.writeValueAsString(data));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("图纸追溯事件序列化失败", exception);
            }
        }
        event.setClientIp(request == null ? null : request.getRemoteAddr());
        event.setCreateTime(now());
        requireSingleWrite(eventMapper.insert(event), "图纸追溯事件写入");
    }

    private void registerRollbackCleanup(StoredFile stored) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    storageManager.deleteQuietly(stored.provider(), stored.storageKey());
                }
            }
        });
    }

    private record PublishedItem(Long incomingItemId, ProjectDocument document,
                                 ProjectDocumentVersion version, Long previousVersionId) { }
}
