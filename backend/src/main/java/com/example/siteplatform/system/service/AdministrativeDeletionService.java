package com.example.siteplatform.system.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.dto.AdministrativeDeletionExecuteRequest;
import com.example.siteplatform.system.dto.AdministrativeDeletionPreviewRequest;
import com.example.siteplatform.system.dto.DeletionImpactVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class AdministrativeDeletionService {
    private static final String TOKEN_PREFIX = "admin:deletion:";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);
    private static final Set<String> SUPPORTED_TARGETS = Set.of(
            "USER", "PROJECT", "ROLE", "REGISTRATION_APPLICATION",
            "DOCUMENT_FOLDER", "PROJECT_DOCUMENT", "FILE",
            "ELECTRIC_BOX", "INSPECTION_RECORD", "QUALITY_ISSUE",
            "SITE_ACCESS_INVITATION");

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final FileStorageManager storageManager;
    private final OperationLogMapper operationLogMapper;
    private final ProjectPermissionService projectPermissionService;
    private final AuthService authService;
    private final ResponsibilityReleaseService responsibilityReleaseService;

    public AdministrativeDeletionService(JdbcTemplate jdbc,
                                         StringRedisTemplate redis,
                                         ObjectMapper objectMapper,
                                         FileStorageManager storageManager,
                                         OperationLogMapper operationLogMapper,
                                         ProjectPermissionService projectPermissionService,
                                         AuthService authService,
                                         ResponsibilityReleaseService responsibilityReleaseService) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.storageManager = storageManager;
        this.operationLogMapper = operationLogMapper;
        this.projectPermissionService = projectPermissionService;
        this.authService = authService;
        this.responsibilityReleaseService = responsibilityReleaseService;
    }

    public DeletionImpactVO preview(AdministrativeDeletionPreviewRequest request, SysUser operator) {
        String type = normalizeType(request.getTargetType());
        if ("USER".equals(type)) validateUserDeletion(request.getTargetId(), operator, false);
        DeletionImpactVO impact = buildImpact(type, request.getTargetId());
        String signature = signature(impact);
        String token = UUID.randomUUID().toString();
        TokenPayload payload = new TokenPayload(type, request.getTargetId(), operator.getId(), impact.getTargetName(), signature);
        try {
            redis.opsForValue().set(TOKEN_PREFIX + token, objectMapper.writeValueAsString(payload), TOKEN_TTL);
        } catch (Exception exception) {
            throw new BusinessException("删除确认令牌生成失败，请稍后重试");
        }
        impact.setConfirmationToken(token);
        return impact;
    }

    @Transactional
    public void execute(AdministrativeDeletionExecuteRequest request, SysUser operator) {
        String type = normalizeType(request.getTargetType());
        if (!request.isAcknowledged()) throw new BusinessException("请确认已了解删除操作不可恢复");
        if ("USER".equals(type)) {
            lockPlatformAdministratorMutex();
            lockTarget(type, request.getTargetId());
            validateUserDeletion(request.getTargetId(), operator, true);
        } else {
            lockTarget(type, request.getTargetId());
        }
        DeletionImpactVO current = buildImpact(type, request.getTargetId());
        validateAndConsumeToken(request, operator, type, current);
        switch (type) {
            case "USER" -> deleteUser(request.getTargetId());
            case "PROJECT" -> deleteProject(request.getTargetId(), operator);
            case "ROLE" -> deleteRole(request.getTargetId(), operator);
            case "REGISTRATION_APPLICATION" -> deleteRegistrationApplication(request.getTargetId());
            case "DOCUMENT_FOLDER" -> deleteDocumentFolder(request.getTargetId());
            case "PROJECT_DOCUMENT" -> deleteProjectDocument(request.getTargetId());
            case "FILE" -> deleteQualityDocumentFile(request.getTargetId());
            case "ELECTRIC_BOX" -> deleteElectricBox(request.getTargetId());
            case "INSPECTION_RECORD" -> deleteInspectionRecord(request.getTargetId());
            case "QUALITY_ISSUE" -> deleteQualityIssue(request.getTargetId());
            case "SITE_ACCESS_INVITATION" -> deleteSiteAccessInvitation(request.getTargetId());
            default -> throw new BusinessException("不支持的删除类型");
        }
        recordDeletion(operator, current);
    }

    public void retryFailedFilePurge(Long fileId, SysUser operator) {
        List<FileResource> resources = files(
                "SELECT * FROM file_resource WHERE id = ? AND deleted = 1 AND status = 'DELETE_FAILED'", fileId);
        if (resources.isEmpty()) throw BusinessException.notFound("待重试的文件清理记录不存在");
        FileResource file = resources.get(0);
        OperationLog log = new OperationLog();
        log.setUserId(operator.getId());
        log.setUsername(operator.getUsername());
        log.setOperationType("ADMIN_FILE_DELETE_RETRY");
        log.setOperationDesc("管理员重试物理文件清理《" + file.getFileName() + "》");
        log.setBusinessType("FILE");
        log.setBusinessId(fileId);
        log.setCreateTime(LocalDateTime.now());
        operationLogMapper.insert(log);
        try {
            storageManager.delete(file);
            requireSingle(update("DELETE FROM file_resource WHERE id = ? AND deleted = 1 "
                    + "AND status = 'DELETE_FAILED'", fileId), "文件清理状态已变化，请刷新后重试");
        } catch (BusinessException exception) {
            update("UPDATE file_resource SET status = 'DELETE_FAILED', update_time = NOW() WHERE id = ?", fileId);
            throw exception;
        }
    }

    private DeletionImpactVO buildImpact(String type, Long id) {
        DeletionImpactVO impact = new DeletionImpactVO();
        impact.setTargetType(type);
        impact.setTargetId(id);
        // 保留返回字段兼容旧客户端；现行交互只需勾选不可恢复确认。
        impact.setTypedConfirmationRequired(false);
        switch (type) {
            case "USER" -> userImpact(impact, id);
            case "PROJECT" -> projectImpact(impact, id);
            case "ROLE" -> roleImpact(impact, id);
            case "REGISTRATION_APPLICATION" -> registrationApplicationImpact(impact, id);
            case "DOCUMENT_FOLDER" -> folderImpact(impact, id);
            case "PROJECT_DOCUMENT" -> documentImpact(impact, id);
            case "FILE" -> fileImpact(impact, id);
            case "ELECTRIC_BOX" -> electricBoxImpact(impact, id);
            case "INSPECTION_RECORD" -> inspectionRecordImpact(impact, id);
            case "QUALITY_ISSUE" -> qualityIssueImpact(impact, id);
            case "SITE_ACCESS_INVITATION" -> siteAccessInvitationImpact(impact, id);
            default -> throw new BusinessException("不支持的删除类型");
        }
        impact.setTotalAssociatedCount(impact.getItems().stream().mapToLong(DeletionImpactVO.Item::getCount).sum());
        return impact;
    }

    private void userImpact(DeletionImpactVO impact, Long id) {
        Map<String, Object> user = requireRow(
                "SELECT id, username, real_name FROM sys_user WHERE id = ? AND deleted = 0", id, "用户不存在");
        String username = text(user.get("username"));
        String realName = text(user.get("real_name"));
        impact.setTargetName(StringUtils.hasText(realName) ? realName + "（" + username + "）" : username);
        add(impact, "projectMemberships", "项目成员关系", count("sys_user_project", "user_id", id));
        add(impact, "roleAssignments", "平台及项目角色", count("sys_user_role", "user_id", id)
                + count("sys_user_project_role", "user_id", id));
        add(impact, "wechatBindings", "微信绑定与订阅状态", count("sys_user_wechat_binding", "user_id", id)
                + count("wechat_subscription_state", "user_id", id));
        add(impact, "personalSettings", "个人视频布局", count("video_layout_config", "user_id", id));
        add(impact, "responsibleBoxes", "将转为待分配的电箱责任", countSql("""
                SELECT COUNT(*) FROM electric_box
                WHERE deleted = 0 AND (responsible_electrician_id = ? OR safety_manager_id = ?)
                """, id, id));
        add(impact, "pendingReviews", "将转为待分配的巡检复核", countSql("""
                SELECT COUNT(*) FROM inspection_record
                WHERE deleted = 0 AND assigned_reviewer_id = ?
                  AND (review_time IS NULL OR status = 'REVIEW_PENDING')
                """, id));
        add(impact, "openRectifications", "将转为待分配的巡检整改", countSql("""
                SELECT COUNT(*) FROM inspection_rectification
                WHERE deleted = 0 AND assignee_id = ? AND status <> 'CLOSED'
                """, id));
        add(impact, "openQualityIssues", "将转为待分配的质量整改", countSql("""
                SELECT COUNT(*) FROM quality_issue
                WHERE deleted = 0 AND assignee_id = ? AND status NOT IN ('CLOSED', 'VOIDED')
                """, id));
        add(impact, "pendingWechatMessages", "将停止发送的微信消息", countSql("""
                SELECT COUNT(*) FROM wechat_message_log WHERE user_id = ? AND status = 'PENDING'
                """, id));
        add(impact, "sealApprovalConfigs", "将移除的用印审批/默认抄送配置",
                count("workflow_approval_config_user", "user_id", id));
        add(impact, "pendingSealApprovals", "将取消并提示管理员改派的用印待办", countSql("""
                SELECT COUNT(*) FROM workflow_approval_task
                WHERE assignee_user_id = ? AND business_code = 'SEAL_APPLICATION' AND status = 'PENDING'
                """, id));
        add(impact, "sealApplications", "保留的本人用印申请",
                count("seal_application", "applicant_id", id));
        add(impact, "sealUploads", "保留的用印附件上传快照",
                count("seal_application_file", "uploader_id", id));
        add(impact, "sealCcHistory", "保留的用印抄送快照",
                count("workflow_cc_recipient", "user_id", id));
        add(impact, "inboxNotifications", "将删除的个人站内通知",
                count("user_notification", "user_id", id));
        add(impact, "preservedHistory", "保留的历史业务与审计引用", preservedUserHistoryCount(id));
    }

    private void projectImpact(DeletionImpactVO impact, Long id) {
        Map<String, Object> project = requireRow(
                "SELECT id, project_name FROM project_info WHERE id = ? AND deleted = 0", id, "项目不存在");
        impact.setTargetName(text(project.get("project_name")));
        add(impact, "members", "成员与角色", count("sys_user_project", "project_id", id));
        add(impact, "documents", "资料与目录", countSql("SELECT COUNT(*) FROM project_document WHERE project_id = ?", id)
                + countSql("SELECT COUNT(*) FROM document_folder WHERE project_id = ?", id));
        add(impact, "boxes", "电箱与二维码", count("electric_box", "project_id", id));
        add(impact, "inspections", "巡检与整改", count("inspection_record", "project_id", id)
                + count("inspection_rectification", "project_id", id));
        add(impact, "quality", "质量问题与日志", count("quality_issue", "project_id", id));
        add(impact, "sealWorkflow", "用印申请、印章、审批、抄送与通知", sealProjectDataCount(id));
        long submittedSealApplications = submittedSealApplicationCount(id);
        add(impact, "preservedSealHistory", "必须保留的已提交用印申请与审批台账", submittedSealApplications);
        if (submittedSealApplications > 0) {
            throw BusinessException.of(409, "项目存在已提交用印申请及审批台账，禁止物理删除；请停用项目并保留审计");
        }
        long siteAccessCount = count("site_visit_invitation", "project_id", id)
                + count("site_visit_person", "project_id", id)
                + count("site_visit_audit_log", "project_id", id);
        add(impact, "siteAccess", "外访邀请、人员与审计", siteAccessCount);
        if (siteAccessCount > 0) {
            throw BusinessException.of(409, "项目存在需长期保留的外访数据，禁止物理删除；请停用项目并保留审计");
        }
        add(impact, "applications", "项目访问申请", count("wechat_access_application", "project_id", id));
        add(impact, "pendingRegistrations", "待审核注册申请", pendingRegistrationApplicationCount(id));
        long other = count("temporary_person", "project_id", id)
                + count("safety_education_batch", "project_id", id)
                + count("camera_resource", "project_id", id)
                + count("device_info", "project_id", id)
                + count("external_system_config", "project_id", id);
        add(impact, "compatibility", "兼容历史业务", other);
        setFileImpact(impact, fileStats("SELECT COUNT(*) file_count, COALESCE(SUM(file_size), 0) file_bytes "
                + "FROM file_resource WHERE project_id = ?", id));
    }

    private void roleImpact(DeletionImpactVO impact, Long id) {
        Map<String, Object> role = requireRow(
                "SELECT id, role_name, role_code FROM sys_role WHERE id = ? AND deleted = 0", id, "角色不存在");
        String code = text(role.get("role_code"));
        if (ProjectPermissionService.ROLE_PLATFORM_ADMIN.equalsIgnoreCase(code)) {
            throw new BusinessException("内置平台管理员角色不能删除");
        }
        if (Set.of(ProjectPermissionService.ROLE_ELECTRICIAN,
                ProjectPermissionService.ROLE_SAFETY_OFFICER).contains(code.toUpperCase())) {
            throw new BusinessException("巡检闭环业务角色不能删除");
        }
        impact.setTargetName(text(role.get("role_name")));
        add(impact, "platformUsers", "平台用户关系", count("sys_user_role", "role_id", id));
        add(impact, "projectUsers", "项目成员角色关系", count("sys_user_project_role", "role_id", id));
        add(impact, "removedMemberships", "将移出项目的成员", countSql("""
                SELECT COUNT(*) FROM sys_user_project_role target
                WHERE target.role_id = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM sys_user_project_role remaining
                      INNER JOIN sys_role remaining_role ON remaining_role.id = remaining.role_id
                      WHERE remaining.user_id = target.user_id
                        AND remaining.project_id = target.project_id
                        AND remaining.role_id <> target.role_id
                        AND remaining_role.enabled = 1
                        AND remaining_role.deleted = 0
                  )
                """, id));
        add(impact, "authorization", "菜单、模块和操作权限", count("sys_role_menu", "role_id", id)
                + count("sys_role_permission", "role_id", id)
                + count("sys_role_business_module", "role_id", id));
        List<Map<String, Object>> assignments = jdbc.queryForList(
                "SELECT DISTINCT user_id, project_id FROM sys_user_project_role WHERE role_id = ?", id);
        long boxes = 0;
        long reviews = 0;
        long rectifications = 0;
        long quality = 0;
        for (Map<String, Object> assignment : assignments) {
            var responsibility = responsibilityReleaseService.impact(
                    number(assignment.get("project_id")), number(assignment.get("user_id")));
            boxes += responsibility.getResponsibleElectricBoxCount()
                    + responsibility.getSafetyManagedElectricBoxCount();
            reviews += responsibility.getPendingInspectionReviewCount();
            rectifications += responsibility.getOpenRectificationCount();
            quality += responsibility.getOpenQualityIssueCount();
        }
        add(impact, "responsibleBoxes", "可能解除的电箱责任", boxes);
        add(impact, "pendingReviews", "可能转待分配的巡检复核", reviews);
        add(impact, "openRectifications", "可能转待分配的巡检整改", rectifications);
        add(impact, "openQualityIssues", "可能转待分配的质量整改", quality);
    }

    private void registrationApplicationImpact(DeletionImpactVO impact, Long id) {
        Map<String, Object> application = requireRow("""
                SELECT id, username, real_name, status, password_hash, desired_project_ids,
                       app_id, openid, created_user_id
                FROM registration_application
                WHERE id = ?
                """, id, "注册申请不存在");
        String applicant = StringUtils.hasText(text(application.get("real_name")))
                ? text(application.get("real_name")) + "（" + text(application.get("username")) + "）"
                : text(application.get("username"));
        impact.setTargetName(registrationStatusLabel(text(application.get("status"))) + " · " + applicant);
        add(impact, "desiredProjects", "将删除的项目申请意向",
                registrationDesiredProjectCount(text(application.get("desired_project_ids"))));
        add(impact, "passwordCredential", "将清除的待审核密码摘要",
                StringUtils.hasText(text(application.get("password_hash"))) ? 1 : 0);
        add(impact, "wechatIdentity", "将清除的申请微信身份",
                StringUtils.hasText(text(application.get("app_id")))
                        && StringUtils.hasText(text(application.get("openid"))) ? 1 : 0);
        add(impact, "preservedUser", "保留的已创建用户及其授权",
                application.get("created_user_id") == null ? 0 : 1);
        add(impact, "preservedAuditLogs", "保留的审核操作日志", countSql("""
                SELECT COUNT(*) FROM sys_operation_log
                WHERE business_type = 'REGISTRATION_APPLICATION' AND business_id = ?
                """, id));
    }

    private void folderImpact(DeletionImpactVO impact, Long id) {
        Map<String, Object> folder = requireRow(
                "SELECT id, folder_name FROM document_folder WHERE id = ?", id, "资料目录不存在");
        impact.setTargetName(text(folder.get("folder_name")));
        List<Long> folderIds = folderTreeIds(id);
        List<Long> documentIds = ids("SELECT id FROM project_document WHERE folder_id IN (" + placeholders(folderIds) + ")", folderIds);
        add(impact, "subfolders", "子目录", Math.max(0, folderIds.size() - 1));
        add(impact, "documents", "目录内资料", documentIds.size());
        add(impact, "versions", "资料版本", countByIds("project_document_version", "document_id", documentIds));
        add(impact, "sealArchiveReferences", "用印归档追溯（禁止单独永久删除）",
                sealArchiveReferenceCount(documentIds));
        setFileImpact(impact, fileStatsForDocuments(documentIds));
    }

    private void documentImpact(DeletionImpactVO impact, Long id) {
        Map<String, Object> document = requireRow(
                "SELECT id, title, deleted FROM project_document WHERE id = ?", id, "资料不存在");
        impact.setTargetName(text(document.get("title")));
        add(impact, "versions", "资料版本", count("project_document_version", "document_id", id));
        add(impact, "sealArchiveReferences", "用印归档追溯（禁止单独永久删除）",
                sealArchiveReferenceCount(List.of(id)));
        setFileImpact(impact, fileStatsForDocuments(List.of(id)));
    }

    private void fileImpact(DeletionImpactVO impact, Long id) {
        Map<String, Object> file = requireRow(
                "SELECT id, file_name, file_size, business_type FROM file_resource WHERE id = ?", id, "文件不存在");
        if (!"QUALITY_DOCUMENT".equalsIgnoreCase(text(file.get("business_type")))) {
            throw new BusinessException("流程附件必须随所属业务记录删除");
        }
        impact.setTargetName(text(file.get("file_name")));
        impact.setFileCount(1);
        impact.setFileBytes(number(file.get("file_size")));
        add(impact, "files", "物理文件", 1);
    }

    private void electricBoxImpact(DeletionImpactVO impact, Long id) {
        Map<String, Object> box = requireRow(
                "SELECT id, project_id, box_code FROM electric_box WHERE id = ?", id, "电箱不存在");
        impact.setTargetName(text(box.get("box_code")));
        List<Long> recordIds = ids("SELECT id FROM inspection_record WHERE electric_box_id = ?", List.of(id));
        List<Long> rectificationIds = ids("SELECT id FROM inspection_rectification WHERE electric_box_id = ?", List.of(id));
        add(impact, "scope", "巡检范围与二维码日志", count("electric_box_inspection_scope", "electric_box_id", id)
                + count("electric_box_qr_log", "electric_box_id", id));
        add(impact, "records", "巡检记录", recordIds.size());
        add(impact, "rectifications", "整改任务", rectificationIds.size());
        setFileImpact(impact, fileStatsForInspection(number(box.get("project_id")), recordIds, rectificationIds));
    }

    private void inspectionRecordImpact(DeletionImpactVO impact, Long id) {
        Map<String, Object> record = requireRow("""
                SELECT ir.id, ir.project_id, ir.check_date, eb.box_code
                FROM inspection_record ir LEFT JOIN electric_box eb ON eb.id = ir.electric_box_id
                WHERE ir.id = ?
                """, id, "巡检记录不存在");
        impact.setTargetName((text(record.get("box_code")) + " " + text(record.get("check_date"))).trim());
        List<Long> rectificationIds = ids("SELECT id FROM inspection_rectification WHERE inspection_record_id = ?", List.of(id));
        add(impact, "items", "检查项", count("inspection_record_item", "record_id", id));
        add(impact, "reviews", "复核留痕", count("inspection_review_log", "record_id", id));
        add(impact, "rectifications", "整改任务及留痕", rectificationIds.size()
                + countByIds("inspection_rectification_review_log", "rectification_id", rectificationIds));
        setFileImpact(impact, fileStatsForInspection(number(record.get("project_id")), List.of(id), rectificationIds));
    }

    private void qualityIssueImpact(DeletionImpactVO impact, Long id) {
        Map<String, Object> issue = requireRow(
                "SELECT id, project_id, issue_no FROM quality_issue WHERE id = ?", id, "质量问题不存在");
        impact.setTargetName(text(issue.get("issue_no")));
        add(impact, "logs", "质量操作留痕", count("quality_issue_log", "issue_id", id));
        Map<String, Object> stats = fileStats("""
                SELECT COUNT(*) file_count, COALESCE(SUM(file_size), 0) file_bytes
                FROM file_resource
                WHERE project_id = ? AND business_id = ?
                  AND business_type IN ('QUALITY_ISSUE', 'QUALITY_RECTIFICATION', 'QUALITY_REVIEW')
                """, number(issue.get("project_id")), id);
        setFileImpact(impact, stats);
    }

    private void siteAccessInvitationImpact(DeletionImpactVO impact, Long id) {
        Map<String, Object> invitation = requireRow("""
                SELECT id, invite_no,
                       CASE
                           WHEN status = 'PENDING' AND visit_end_time < NOW() THEN 'EXPIRED'
                           ELSE status
                       END AS effective_status
                FROM site_visit_invitation
                WHERE id = ? AND deleted = 0
                """, id, "外访邀请不存在");
        impact.setTargetName(siteAccessStatusLabel(text(invitation.get("effective_status")))
                + " · " + text(invitation.get("invite_no")));
        add(impact, "invitation", "外访邀请及加密联系信息", 1);
        add(impact, "visitors", "外访人员实名信息",
                count("site_visit_person", "invitation_id", id));
        add(impact, "businessAuditLogs", "场内业务审计记录",
                count("site_visit_audit_log", "invitation_id", id));
        add(impact, "preservedOperationLogs", "保留的平台操作日志", countSql("""
                SELECT COUNT(*) FROM sys_operation_log
                WHERE business_type = 'SITE_ACCESS' AND business_id = ?
                """, id));
    }

    private void lockTarget(String type, Long id) {
        String table = switch (type) {
            case "USER" -> "sys_user";
            case "PROJECT" -> "project_info";
            case "ROLE" -> "sys_role";
            case "REGISTRATION_APPLICATION" -> "registration_application";
            case "DOCUMENT_FOLDER" -> "document_folder";
            case "PROJECT_DOCUMENT" -> "project_document";
            case "FILE" -> "file_resource";
            case "ELECTRIC_BOX" -> "electric_box";
            case "INSPECTION_RECORD" -> "inspection_record";
            case "QUALITY_ISSUE" -> "quality_issue";
            case "SITE_ACCESS_INVITATION" -> "site_visit_invitation";
            default -> throw new BusinessException("不支持的删除类型");
        };
        requireRow("SELECT id FROM `" + table + "` WHERE id = ? FOR UPDATE", id, "待删除数据不存在");
    }

    private void deleteUser(Long userId) {
        Set<Long> projectIds = new LinkedHashSet<>(ids("""
                SELECT project_id FROM sys_user_project WHERE user_id = ?
                UNION SELECT project_id FROM electric_box
                    WHERE responsible_electrician_id = ? OR safety_manager_id = ?
                UNION SELECT project_id FROM inspection_record WHERE assigned_reviewer_id = ?
                UNION SELECT project_id FROM inspection_rectification WHERE assignee_id = ?
                UNION SELECT project_id FROM quality_issue WHERE assignee_id = ?
                UNION SELECT project_id FROM workflow_approval_config_user WHERE user_id = ?
                UNION SELECT project_id FROM workflow_approval_task
                    WHERE assignee_user_id = ? AND business_code = 'SEAL_APPLICATION' AND status = 'PENDING'
                """, List.of(userId, userId, userId, userId, userId, userId, userId, userId)));
        projectIds.stream().filter(Objects::nonNull).forEach(projectId ->
                responsibilityReleaseService.releaseAll(projectId, userId));

        update("UPDATE wechat_message_log SET status = 'SKIPPED', response_message = ?, update_time = NOW() "
                        + "WHERE user_id = ? AND status = 'PENDING'",
                "用户已由平台管理员删除，消息不再发送", userId);
        update("UPDATE wechat_access_application SET matched_user_id = NULL, update_time = NOW() "
                + "WHERE matched_user_id = ? AND status = 'PENDING'", userId);
        update("DELETE FROM wechat_subscription_state WHERE user_id = ?", userId);
        update("DELETE FROM sys_user_wechat_binding WHERE user_id = ?", userId);
        update("DELETE FROM video_layout_config WHERE user_id = ?", userId);
        update("DELETE FROM user_notification WHERE user_id = ?", userId);
        update("DELETE FROM workflow_approval_config_user WHERE user_id = ?", userId);
        update("DELETE FROM sys_user_project_role WHERE user_id = ?", userId);
        update("DELETE FROM sys_user_project WHERE user_id = ?", userId);
        update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
        requireSingle(update("DELETE FROM sys_user WHERE id = ? AND deleted = 0", userId),
                "用户状态已变化，请重新预览");
        invalidateUsers(List.of(userId));
    }

    private void deleteProject(Long projectId, SysUser operator) {
        if (submittedSealApplicationCount(projectId) > 0) {
            throw BusinessException.of(409, "项目存在已提交用印申请及审批台账，禁止物理删除；请停用项目并保留审计");
        }
        long siteAccessCount = count("site_visit_invitation", "project_id", projectId)
                + count("site_visit_person", "project_id", projectId)
                + count("site_visit_audit_log", "project_id", projectId);
        if (siteAccessCount > 0) {
            throw BusinessException.of(409, "项目存在需长期保留的外访数据，禁止物理删除；请停用项目并保留审计");
        }
        Set<Long> affectedUsers = new LinkedHashSet<>(ids(
                "SELECT user_id FROM sys_user_project WHERE project_id = ?", List.of(projectId)));
        List<FileResource> files = files("SELECT * FROM file_resource WHERE project_id = ?", projectId);
        stageFiles(files);
        reconcilePendingRegistrationApplications(projectId, operator);

        // Only draft-only projects can reach this path. Submitted/decided applications are
        // immutable audit records and are blocked above instead of being cascade-deleted.
        update("DELETE FROM user_notification WHERE project_id = ?", projectId);
        update("DELETE FROM workflow_approval_task WHERE project_id = ?", projectId);
        update("DELETE FROM workflow_approval_instance WHERE project_id = ?", projectId);
        update("DELETE FROM workflow_cc_recipient WHERE project_id = ?", projectId);
        update("DELETE FROM workflow_approval_config_user WHERE project_id = ?", projectId);
        update("DELETE FROM workflow_approval_config WHERE project_id = ?", projectId);
        update("DELETE FROM seal_application_file WHERE project_id = ?", projectId);
        update("DELETE FROM seal_application_item WHERE project_id = ?", projectId);
        update("DELETE FROM seal_application_log WHERE project_id = ?", projectId);
        update("DELETE FROM seal_application WHERE project_id = ?", projectId);
        update("DELETE FROM seal_definition WHERE project_id = ?", projectId);

        update("DELETE FROM safety_education_person WHERE batch_id IN "
                + "(SELECT id FROM safety_education_batch WHERE project_id = ?) OR person_id IN "
                + "(SELECT id FROM temporary_person WHERE project_id = ?)", projectId, projectId);
        update("DELETE FROM project_document_version WHERE document_id IN "
                + "(SELECT id FROM project_document WHERE project_id = ?)", projectId);
        update("DELETE FROM device_status_record WHERE device_id IN (SELECT id FROM device_info WHERE project_id = ?)", projectId);
        update("DELETE FROM inspection_rectification_review_log WHERE project_id = ?", projectId);
        update("DELETE FROM inspection_review_log WHERE project_id = ?", projectId);
        update("DELETE FROM inspection_record_item WHERE record_id IN "
                + "(SELECT id FROM inspection_record WHERE project_id = ?)", projectId);

        for (String table : List.of(
                "quality_issue_log", "quality_issue", "inspection_rectification", "inspection_record",
                "electric_box_inspection_scope", "electric_box_qr_log", "project_inspection_setting",
                "project_document", "document_folder", "person_certificate", "person_entry_exit_log",
                "safety_education_batch", "temporary_person", "video_access_log", "video_layout_config",
                "camera_resource", "device_info", "external_system_config", "wechat_access_application",
                "sys_user_project_role", "sys_user_project", "electric_box")) {
            update("DELETE FROM `" + table + "` WHERE project_id = ?", projectId);
        }
        requireSingle(update("DELETE FROM project_info WHERE id = ?", projectId), "项目状态已变化，请重新预览");
        invalidateUsers(affectedUsers);
        registerCommittedFilePurge(files);
    }

    private void deleteRole(Long roleId, SysUser operator) {
        Map<String, Object> role = requireRow(
                "SELECT role_code FROM sys_role WHERE id = ?", roleId, "角色不存在");
        String roleCode = text(role.get("role_code"));
        if (ProjectPermissionService.ROLE_PLATFORM_ADMIN.equalsIgnoreCase(roleCode)) {
            throw new BusinessException("内置平台管理员角色不能删除");
        }
        if (Set.of(ProjectPermissionService.ROLE_ELECTRICIAN,
                ProjectPermissionService.ROLE_SAFETY_OFFICER).contains(roleCode.toUpperCase())) {
            throw new BusinessException("巡检闭环业务角色不能删除");
        }
        Set<Long> affectedUsers = new LinkedHashSet<>(ids(
                "SELECT user_id FROM sys_user_role WHERE role_id = ?", List.of(roleId)));
        List<Map<String, Object>> projectAssignments = jdbc.queryForList(
                "SELECT DISTINCT user_id, project_id FROM sys_user_project_role WHERE role_id = ?", roleId);
        projectAssignments.forEach(row -> affectedUsers.add(number(row.get("user_id"))));

        update("DELETE FROM sys_user_role WHERE role_id = ?", roleId);
        update("DELETE FROM sys_user_project_role WHERE role_id = ?", roleId);
        for (Map<String, Object> assignment : projectAssignments) {
            Long userId = number(assignment.get("user_id"));
            Long projectId = number(assignment.get("project_id"));
            if (countSql("SELECT COUNT(*) FROM sys_user_project_role WHERE user_id = ? AND project_id = ?", userId, projectId) == 0) {
                update("DELETE FROM sys_user_project WHERE user_id = ? AND project_id = ?", userId, projectId);
                responsibilityReleaseService.releaseAll(projectId, userId);
            } else {
                List<String> remainingRoleCodes = jdbc.query("""
                        SELECT role.role_code
                        FROM sys_user_project_role relation
                        INNER JOIN sys_role role ON role.id = relation.role_id
                        WHERE relation.user_id = ? AND relation.project_id = ?
                          AND role.enabled = 1 AND role.deleted = 0
                        ORDER BY role.project_manager_role DESC, role.role_code ASC
                        LIMIT 1
                        """, (rs, rowNum) -> rs.getString(1), userId, projectId);
                if (remainingRoleCodes.isEmpty()) {
                    update("DELETE FROM sys_user_project WHERE user_id = ? AND project_id = ?", userId, projectId);
                    responsibilityReleaseService.releaseAll(projectId, userId);
                } else {
                    update("UPDATE sys_user_project SET project_role_code = ?, update_time = NOW() "
                            + "WHERE user_id = ? AND project_id = ?", remainingRoleCodes.get(0), userId, projectId);
                    responsibilityReleaseService.releaseForCapabilityLoss(projectId, userId);
                }
            }
        }
        update("DELETE FROM sys_role_menu WHERE role_id = ?", roleId);
        update("DELETE FROM sys_role_permission WHERE role_id = ?", roleId);
        update("DELETE FROM sys_role_business_module WHERE role_id = ?", roleId);
        requireSingle(update("DELETE FROM sys_role WHERE id = ?", roleId), "角色状态已变化，请重新预览");
        invalidateUsers(affectedUsers);
    }

    private void deleteRegistrationApplication(Long applicationId) {
        requireSingle(update("DELETE FROM registration_application WHERE id = ?", applicationId),
                "注册申请状态已变化，请重新预览");
    }

    private void deleteDocumentFolder(Long folderId) {
        List<Long> folderIds = folderTreeIds(folderId);
        List<Long> documentIds = ids("SELECT id FROM project_document WHERE folder_id IN (" + placeholders(folderIds) + ")", folderIds);
        requireNoSealArchiveReference(documentIds);
        List<FileResource> files = filesForDocuments(documentIds);
        stageFiles(files);
        deleteByIds("project_document_version", "document_id", documentIds);
        deleteByIds("project_document", "id", documentIds);
        deleteByIds("document_folder", "id", folderIds);
        registerCommittedFilePurge(files);
    }

    private void deleteProjectDocument(Long documentId) {
        Map<String, Object> document = requireRow(
                "SELECT deleted FROM project_document WHERE id = ?", documentId, "资料不存在");
        if (number(document.get("deleted")) != 1L) throw new BusinessException("请先将资料移入回收站再永久删除");
        requireNoSealArchiveReference(List.of(documentId));
        List<FileResource> files = filesForDocuments(List.of(documentId));
        stageFiles(files);
        update("DELETE FROM project_document_version WHERE document_id = ?", documentId);
        requireSingle(update("DELETE FROM project_document WHERE id = ?", documentId), "资料状态已变化，请重新预览");
        registerCommittedFilePurge(files);
    }

    private void deleteQualityDocumentFile(Long fileId) {
        Map<String, Object> file = requireRow(
                "SELECT business_type FROM file_resource WHERE id = ?", fileId, "文件不存在");
        if (!"QUALITY_DOCUMENT".equalsIgnoreCase(text(file.get("business_type")))) {
            throw new BusinessException("流程附件必须随所属业务记录删除");
        }
        List<FileResource> files = files("SELECT * FROM file_resource WHERE id = ?", fileId);
        stageFiles(files);
        registerCommittedFilePurge(files);
    }

    private void deleteElectricBox(Long boxId) {
        Map<String, Object> box = requireRow(
                "SELECT project_id FROM electric_box WHERE id = ?", boxId, "电箱不存在");
        Long projectId = number(box.get("project_id"));
        List<Long> recordIds = ids("SELECT id FROM inspection_record WHERE electric_box_id = ?", List.of(boxId));
        List<Long> rectificationIds = ids("SELECT id FROM inspection_rectification WHERE electric_box_id = ?", List.of(boxId));
        List<FileResource> files = filesForInspection(projectId, recordIds, rectificationIds);
        stageFiles(files);
        deleteByIds("inspection_rectification_review_log", "rectification_id", rectificationIds);
        deleteByIds("inspection_record_item", "record_id", recordIds);
        deleteByIds("inspection_review_log", "record_id", recordIds);
        deleteByIds("inspection_rectification", "id", rectificationIds);
        deleteByIds("inspection_record", "id", recordIds);
        update("DELETE FROM electric_box_inspection_scope WHERE electric_box_id = ?", boxId);
        update("DELETE FROM electric_box_qr_log WHERE electric_box_id = ?", boxId);
        requireSingle(update("DELETE FROM electric_box WHERE id = ?", boxId), "电箱状态已变化，请重新预览");
        registerCommittedFilePurge(files);
    }

    private void deleteInspectionRecord(Long recordId) {
        Map<String, Object> record = requireRow(
                "SELECT project_id FROM inspection_record WHERE id = ?", recordId, "巡检记录不存在");
        List<Long> rectificationIds = ids(
                "SELECT id FROM inspection_rectification WHERE inspection_record_id = ?", List.of(recordId));
        List<FileResource> files = filesForInspection(number(record.get("project_id")), List.of(recordId), rectificationIds);
        stageFiles(files);
        deleteByIds("inspection_rectification_review_log", "rectification_id", rectificationIds);
        update("DELETE FROM inspection_record_item WHERE record_id = ?", recordId);
        update("DELETE FROM inspection_review_log WHERE record_id = ?", recordId);
        deleteByIds("inspection_rectification", "id", rectificationIds);
        requireSingle(update("DELETE FROM inspection_record WHERE id = ?", recordId), "巡检记录状态已变化，请重新预览");
        registerCommittedFilePurge(files);
    }

    private void deleteQualityIssue(Long issueId) {
        Map<String, Object> issue = requireRow(
                "SELECT project_id FROM quality_issue WHERE id = ?", issueId, "质量问题不存在");
        List<FileResource> files = files("""
                SELECT * FROM file_resource
                WHERE project_id = ? AND business_id = ?
                  AND business_type IN ('QUALITY_ISSUE', 'QUALITY_RECTIFICATION', 'QUALITY_REVIEW')
                """, number(issue.get("project_id")), issueId);
        stageFiles(files);
        update("DELETE FROM quality_issue_log WHERE issue_id = ?", issueId);
        requireSingle(update("DELETE FROM quality_issue WHERE id = ?", issueId), "质量问题状态已变化，请重新预览");
        registerCommittedFilePurge(files);
    }

    private void deleteSiteAccessInvitation(Long invitationId) {
        update("DELETE FROM site_visit_person WHERE invitation_id = ?", invitationId);
        update("DELETE FROM site_visit_audit_log WHERE invitation_id = ?", invitationId);
        requireSingle(update("DELETE FROM site_visit_invitation WHERE id = ? AND deleted = 0", invitationId),
                "外访邀请状态已变化，请重新预览");
    }

    private void reconcilePendingRegistrationApplications(Long projectId, SysUser operator) {
        List<Map<String, Object>> applications = jdbc.queryForList(
                "SELECT id, desired_project_ids FROM registration_application WHERE status = 'PENDING' FOR UPDATE");
        for (Map<String, Object> row : applications) {
            String raw = text(row.get("desired_project_ids"));
            if (!StringUtils.hasText(raw)) continue;
            try {
                List<Long> ids = new ArrayList<>(objectMapper.readValue(raw, new TypeReference<List<Long>>() {}));
                if (!ids.removeIf(id -> Objects.equals(id, projectId))) continue;
                String next = objectMapper.writeValueAsString(ids);
                Long applicationId = number(row.get("id"));
                if (ids.isEmpty()) {
                    update("""
                            UPDATE registration_application
                            SET desired_project_ids = ?, status = 'REJECTED', password_hash = NULL,
                                reviewer_id = ?, reviewer_name = ?, review_comment = ?, review_time = NOW(), update_time = NOW()
                            WHERE id = ? AND status = 'PENDING'
                            """, next, operator.getId(), operator.getRealName(), "申请项目已被管理员删除", applicationId);
                } else {
                    update("UPDATE registration_application SET desired_project_ids = ?, update_time = NOW() "
                            + "WHERE id = ? AND status = 'PENDING'", next, applicationId);
                }
            } catch (Exception exception) {
                throw new BusinessException("待审核注册申请项目数据异常，项目删除已取消");
            }
        }
    }

    private long pendingRegistrationApplicationCount(Long projectId) {
        List<Map<String, Object>> applications = jdbc.queryForList(
                "SELECT desired_project_ids FROM registration_application WHERE status = 'PENDING'");
        long count = 0;
        for (Map<String, Object> row : applications) {
            String raw = text(row.get("desired_project_ids"));
            if (!StringUtils.hasText(raw)) continue;
            try {
                List<Long> ids = objectMapper.readValue(raw, new TypeReference<List<Long>>() {});
                if (ids.stream().anyMatch(id -> Objects.equals(id, projectId))) count++;
            } catch (Exception exception) {
                throw new BusinessException("待审核注册申请项目数据异常，无法计算删除影响");
            }
        }
        return count;
    }

    private long preservedUserHistoryCount(Long userId) {
        return count("sys_operation_log", "user_id", userId)
                + count("registration_application", "created_user_id", userId)
                + count("registration_application", "reviewer_id", userId)
                + count("wechat_access_application", "matched_user_id", userId)
                + count("wechat_access_application", "reviewer_id", userId)
                + countSql("SELECT COUNT(*) FROM wechat_message_log WHERE user_id = ? AND status <> 'PENDING'", userId)
                + count("file_resource", "uploader_id", userId)
                + count("document_folder", "created_by", userId)
                + count("project_document", "created_by", userId)
                + count("project_document_version", "created_by", userId)
                + count("electric_box_qr_log", "operator_user_id", userId)
                + count("electric_box_inspection_scope", "operator_id", userId)
                + count("inspection_review_log", "operator_id", userId)
                + count("inspection_rectification_review_log", "operator_id", userId)
                + count("quality_issue_log", "operator_id", userId)
                + count("person_entry_exit_log", "operator_id", userId)
                + count("video_access_log", "user_id", userId)
                + count("seal_definition", "created_by", userId)
                + count("seal_definition", "updated_by", userId)
                + count("seal_application", "approver_id", userId)
                + count("seal_application_log", "operator_id", userId)
                + count("workflow_approval_instance", "initiator_id", userId)
                + count("workflow_approval_instance", "decision_user_id", userId)
                + count("workflow_approval_task", "assignee_user_id", userId)
                + count("workflow_approval_task", "decision_user_id", userId);
    }

    private long sealProjectDataCount(Long projectId) {
        return count("seal_definition", "project_id", projectId)
                + count("seal_application", "project_id", projectId)
                + count("seal_application_item", "project_id", projectId)
                + count("seal_application_file", "project_id", projectId)
                + count("seal_application_log", "project_id", projectId)
                + count("workflow_approval_config", "project_id", projectId)
                + count("workflow_approval_config_user", "project_id", projectId)
                + count("workflow_approval_instance", "project_id", projectId)
                + count("workflow_approval_task", "project_id", projectId)
                + count("workflow_cc_recipient", "project_id", projectId)
                + count("user_notification", "project_id", projectId);
    }

    private long submittedSealApplicationCount(Long projectId) {
        return countSql("""
                SELECT COUNT(*) FROM seal_application
                WHERE project_id = ? AND deleted = 0 AND status <> 'DRAFT'
                """, projectId);
    }

    private long sealArchiveReferenceCount(List<Long> documentIds) {
        if (documentIds.isEmpty()) return 0;
        String placeholders = placeholders(documentIds);
        List<Object> args = new ArrayList<>(documentIds);
        args.addAll(documentIds);
        return countSql("""
                SELECT COUNT(*) FROM seal_application_file relation
                WHERE relation.deleted = 0 AND (
                    relation.archived_document_id IN (%s)
                    OR relation.archived_version_id IN (
                        SELECT version.id FROM project_document_version version
                        WHERE version.document_id IN (%s)
                    )
                )
                """.formatted(placeholders, placeholders), args.toArray());
    }

    private void requireNoSealArchiveReference(List<Long> documentIds) {
        if (sealArchiveReferenceCount(documentIds) > 0) {
            throw BusinessException.of(409, "资料存在用印归档追溯，禁止单独永久删除；请保留审计链");
        }
    }

    private void validateUserDeletion(Long userId, SysUser operator, boolean execution) {
        if (operator == null || operator.getId() == null) {
            throw BusinessException.of(403, "仅平台管理员可以删除用户");
        }
        if (Objects.equals(userId, operator.getId())) {
            throw new BusinessException("不能删除当前登录账号");
        }
        if (execution && !isCurrentPlatformAdministrator(operator.getId())) {
            throw BusinessException.of(403, "当前平台管理员状态已变化，请重新登录");
        }
        if (isRecoverablePlatformAdministrator(userId) && recoverablePlatformAdministratorCount() <= 1) {
            throw new BusinessException("不能删除最后一个可恢复的平台管理员");
        }
    }

    private boolean isCurrentPlatformAdministrator(Long userId) {
        return countSql("""
                SELECT COUNT(DISTINCT u.id)
                FROM sys_user u
                INNER JOIN sys_user_role ur ON ur.user_id = u.id
                INNER JOIN sys_role r ON r.id = ur.role_id
                WHERE u.id = ? AND u.status = 1 AND u.deleted = 0
                  AND r.role_code = 'PLATFORM_ADMIN'
                  AND r.scope_type = 'PLATFORM'
                  AND r.enabled = 1 AND r.deleted = 0
                """, userId) > 0;
    }

    private boolean isRecoverablePlatformAdministrator(Long userId) {
        return countSql("""
                SELECT COUNT(DISTINCT u.id)
                FROM sys_user u
                INNER JOIN sys_user_role ur ON ur.user_id = u.id
                INNER JOIN sys_role r ON r.id = ur.role_id
                WHERE u.id = ? AND u.status = 1 AND u.deleted = 0
                  AND u.password_login_enabled = 1
                  AND u.password_reset_required = 0
                  AND u.password REGEXP '^[$]2[aby][$][0-9]{2}[$].{53}$'
                  AND r.role_code = 'PLATFORM_ADMIN'
                  AND r.scope_type = 'PLATFORM'
                  AND r.enabled = 1 AND r.deleted = 0
                """, userId) > 0;
    }

    private long recoverablePlatformAdministratorCount() {
        return countSql("""
                SELECT COUNT(DISTINCT u.id)
                FROM sys_user u
                INNER JOIN sys_user_role ur ON ur.user_id = u.id
                INNER JOIN sys_role r ON r.id = ur.role_id
                WHERE u.status = 1 AND u.deleted = 0
                  AND u.password_login_enabled = 1
                  AND u.password_reset_required = 0
                  AND u.password REGEXP '^[$]2[aby][$][0-9]{2}[$].{53}$'
                  AND r.role_code = 'PLATFORM_ADMIN'
                  AND r.scope_type = 'PLATFORM'
                  AND r.enabled = 1 AND r.deleted = 0
                """);
    }

    private void lockPlatformAdministratorMutex() {
        List<Map<String, Object>> roles = jdbc.queryForList("""
                SELECT id FROM sys_role
                WHERE role_code = 'PLATFORM_ADMIN' AND scope_type = 'PLATFORM'
                  AND enabled = 1 AND deleted = 0
                ORDER BY id LIMIT 1 FOR UPDATE
                """);
        if (roles.isEmpty()) {
            throw BusinessException.of(409, "平台管理员角色不存在，禁止执行可能导致系统锁定的操作");
        }
    }

    private void validateAndConsumeToken(AdministrativeDeletionExecuteRequest request, SysUser operator,
                                         String type, DeletionImpactVO current) {
        String key = TOKEN_PREFIX + trim(request.getConfirmationToken());
        String raw = redis.opsForValue().get(key);
        if (!StringUtils.hasText(raw)) throw BusinessException.of(409, "删除确认已过期，请重新预览");
        try {
            TokenPayload payload = objectMapper.readValue(raw, TokenPayload.class);
            if (!Objects.equals(type, payload.targetType())
                    || !Objects.equals(request.getTargetId(), payload.targetId())
                    || !Objects.equals(operator.getId(), payload.operatorId())
                    || !Objects.equals(payload.signature(), signature(current))) {
                throw BusinessException.of(409, "关联数据已发生变化，请重新预览确认");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw BusinessException.of(409, "删除确认无效，请重新预览");
        } finally {
            redis.delete(key);
        }
    }

    private void recordDeletion(SysUser operator, DeletionImpactVO impact) {
        OperationLog log = new OperationLog();
        log.setUserId(operator.getId());
        log.setUsername(operator.getUsername());
        log.setOperationType("ADMIN_FORCE_DELETE");
        String detail = impact.getItems().stream()
                .filter(item -> item.getCount() > 0)
                .map(item -> item.getLabel() + item.getCount())
                .reduce((left, right) -> left + "、" + right).orElse("无关联数据");
        log.setOperationDesc("管理员永久删除" + impact.getTargetType() + "《" + impact.getTargetName() + "》：" + detail);
        log.setBusinessType(impact.getTargetType());
        log.setBusinessId(impact.getTargetId());
        log.setCreateTime(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    private void invalidateUsers(Collection<Long> userIds) {
        for (Long userId : userIds.stream().filter(Objects::nonNull).distinct().toList()) {
            projectPermissionService.clearUserProjectsCache(userId);
            authService.logout(userId);
            authService.repeatLogoutAfterCommit(userId);
        }
    }

    private void stageFiles(List<FileResource> files) {
        if (files.isEmpty()) return;
        List<Long> fileIds = files.stream().map(FileResource::getId).toList();
        update("UPDATE file_resource SET deleted = 1, status = 'PENDING_DELETE', update_time = NOW() "
                + "WHERE id IN (" + placeholders(fileIds) + ")", fileIds.toArray());
    }

    private void registerCommittedFilePurge(List<FileResource> files) {
        if (files.isEmpty()) return;
        Runnable purge = () -> {
            for (FileResource file : files) {
                try {
                    storageManager.delete(file);
                    jdbc.update("DELETE FROM file_resource WHERE id = ?", file.getId());
                } catch (Exception exception) {
                    jdbc.update("UPDATE file_resource SET deleted = 1, status = 'DELETE_FAILED', update_time = NOW() WHERE id = ?", file.getId());
                }
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            purge.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                purge.run();
            }
        });
    }

    private List<FileResource> filesForDocuments(List<Long> documentIds) {
        if (documentIds.isEmpty()) return List.of();
        return files("SELECT f.* FROM file_resource f INNER JOIN project_document_version v "
                + "ON v.file_resource_id = f.id WHERE v.document_id IN (" + placeholders(documentIds) + ")",
                documentIds.toArray());
    }

    private List<FileResource> filesForInspection(Long projectId, List<Long> recordIds, List<Long> rectificationIds) {
        List<Object> args = new ArrayList<>();
        args.add(projectId);
        List<String> clauses = new ArrayList<>();
        if (!recordIds.isEmpty()) {
            clauses.add("(business_type = 'INSPECTION_RECORD' AND business_id IN (" + placeholders(recordIds) + "))");
            args.addAll(recordIds);
        }
        if (!rectificationIds.isEmpty()) {
            clauses.add("(business_type = 'INSPECTION_RECTIFICATION' AND business_id IN (" + placeholders(rectificationIds) + "))");
            args.addAll(rectificationIds);
        }
        if (clauses.isEmpty()) return List.of();
        return files("SELECT * FROM file_resource WHERE project_id = ? AND (" + String.join(" OR ", clauses) + ")",
                args.toArray());
    }

    private Map<String, Object> fileStatsForDocuments(List<Long> documentIds) {
        if (documentIds.isEmpty()) return Map.of("file_count", 0L, "file_bytes", 0L);
        return fileStats("SELECT COUNT(*) file_count, COALESCE(SUM(f.file_size), 0) file_bytes "
                + "FROM file_resource f INNER JOIN project_document_version v ON v.file_resource_id = f.id "
                + "WHERE v.document_id IN (" + placeholders(documentIds) + ")", documentIds.toArray());
    }

    private Map<String, Object> fileStatsForInspection(Long projectId, List<Long> recordIds, List<Long> rectificationIds) {
        List<FileResource> resources = filesForInspection(projectId, recordIds, rectificationIds);
        return Map.of("file_count", (long) resources.size(), "file_bytes",
                resources.stream().mapToLong(file -> file.getFileSize() == null ? 0L : file.getFileSize()).sum());
    }

    private void setFileImpact(DeletionImpactVO impact, Map<String, Object> stats) {
        impact.setFileCount(number(stats.get("file_count")));
        impact.setFileBytes(number(stats.get("file_bytes")));
        add(impact, "files", "关联文件", impact.getFileCount());
    }

    private Map<String, Object> fileStats(String sql, Object... args) {
        return jdbc.queryForMap(sql, args);
    }

    private List<FileResource> files(String sql, Object... args) {
        return jdbc.query(sql, (rs, rowNum) -> {
            FileResource file = new FileResource();
            file.setId(rs.getLong("id"));
            file.setProjectId(rs.getLong("project_id"));
            file.setFileName(rs.getString("file_name"));
            file.setFilePath(rs.getString("file_path"));
            file.setStorageProvider(rs.getString("storage_provider"));
            file.setStorageKey(rs.getString("storage_key"));
            long size = rs.getLong("file_size");
            file.setFileSize(rs.wasNull() ? null : size);
            file.setBusinessType(rs.getString("business_type"));
            file.setBusinessId(rs.getObject("business_id", Long.class));
            return file;
        }, args);
    }

    private List<Long> folderTreeIds(Long rootId) {
        return ids("""
                WITH RECURSIVE folder_tree AS (
                    SELECT id FROM document_folder WHERE id = ?
                    UNION ALL
                    SELECT child.id FROM document_folder child
                    INNER JOIN folder_tree parent ON child.parent_id = parent.id
                )
                SELECT id FROM folder_tree
                """, List.of(rootId));
    }

    private List<Long> ids(String sql, List<?> args) {
        return jdbc.query(sql, (rs, rowNum) -> rs.getLong(1), args.toArray());
    }

    private void deleteByIds(String table, String column, List<Long> ids) {
        if (ids.isEmpty()) return;
        update("DELETE FROM `" + table + "` WHERE `" + column + "` IN (" + placeholders(ids) + ")", ids.toArray());
    }

    private long countByIds(String table, String column, List<Long> ids) {
        if (ids.isEmpty()) return 0;
        return countSql("SELECT COUNT(*) FROM `" + table + "` WHERE `" + column + "` IN (" + placeholders(ids) + ")",
                ids.toArray());
    }

    private long count(String table, String column, Long id) {
        return countSql("SELECT COUNT(*) FROM `" + table + "` WHERE `" + column + "` = ?", id);
    }

    private long countSql(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private Map<String, Object> requireRow(String sql, Long id, String message) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, id);
        if (rows.isEmpty()) throw BusinessException.notFound(message);
        return rows.get(0);
    }

    private int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }

    private void requireSingle(int rows, String message) {
        if (rows != 1) throw BusinessException.of(409, message);
    }

    private void add(DeletionImpactVO impact, String code, String label, long count) {
        impact.getItems().add(new DeletionImpactVO.Item(code, label, count));
    }

    private String signature(DeletionImpactVO impact) {
        StringBuilder value = new StringBuilder()
                .append(impact.getTargetType()).append('|')
                .append(impact.getTargetId()).append('|')
                .append(impact.getTargetName()).append('|')
                .append(impact.getFileCount()).append('|')
                .append(impact.getFileBytes());
        impact.getItems().forEach(item -> value.append('|').append(item.getCode()).append(':').append(item.getCount()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    private String normalizeType(String raw) {
        String type = trim(raw).toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TARGETS.contains(type)) throw new BusinessException("不支持的删除类型");
        return type;
    }

    private long registrationDesiredProjectCount(String raw) {
        if (!StringUtils.hasText(raw)) return 0;
        try {
            return objectMapper.readValue(raw, new TypeReference<List<Long>>() {}).stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
        } catch (Exception exception) {
            throw BusinessException.of(409, "注册申请项目数据异常，无法计算删除影响");
        }
    }

    private String registrationStatusLabel(String status) {
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "PENDING" -> "待审核";
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已驳回";
            case "CANCELLED" -> "已取消";
            default -> StringUtils.hasText(status) ? status : "未知状态";
        };
    }

    private String siteAccessStatusLabel(String status) {
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "PENDING" -> "待填写";
            case "SUBMITTED" -> "已提交";
            case "EXPIRED" -> "已过期";
            case "VOIDED" -> "已作废";
            default -> StringUtils.hasText(status) ? status : "未知状态";
        };
    }

    private String placeholders(Collection<?> values) {
        return String.join(",", java.util.Collections.nCopies(values.size(), "?"));
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long number(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private record TokenPayload(String targetType, Long targetId, Long operatorId,
                                String targetName, String signature) {}
}
