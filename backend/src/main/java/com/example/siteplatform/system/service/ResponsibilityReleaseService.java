package com.example.siteplatform.system.service;

import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.notification.service.UserNotificationService;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.dto.ResponsibilityImpactVO;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ResponsibilityReleaseService {
    private final JdbcTemplate jdbc;
    private final SystemPermissionService permissionService;
    private final UserNotificationService notificationService;

    public ResponsibilityReleaseService(JdbcTemplate jdbc, SystemPermissionService permissionService,
                                        UserNotificationService notificationService) {
        this.jdbc = jdbc;
        this.permissionService = permissionService;
        this.notificationService = notificationService;
    }

    public ResponsibilityImpactVO impact(Long projectId, Long userId) {
        ResponsibilityImpactVO impact = new ResponsibilityImpactVO();
        impact.setProjectId(projectId);
        impact.setUserId(userId);
        impact.setProjectName(jdbc.query(
                "SELECT project_name FROM project_info WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                projectId));
        impact.setResponsibleElectricBoxCount(count("""
                SELECT COUNT(*) FROM electric_box
                WHERE project_id = ? AND responsible_electrician_id = ? AND deleted = 0
                """, projectId, userId));
        impact.setSafetyManagedElectricBoxCount(count("""
                SELECT COUNT(*) FROM electric_box
                WHERE project_id = ? AND safety_manager_id = ? AND deleted = 0
                """, projectId, userId));
        impact.setPendingInspectionReviewCount(count("""
                SELECT COUNT(*) FROM inspection_record
                WHERE project_id = ? AND assigned_reviewer_id = ? AND deleted = 0
                  AND (review_time IS NULL OR status = 'REVIEW_PENDING')
                """, projectId, userId));
        impact.setOpenRectificationCount(count("""
                SELECT COUNT(*) FROM inspection_rectification
                WHERE project_id = ? AND assignee_id = ? AND deleted = 0 AND status <> 'CLOSED'
                """, projectId, userId));
        impact.setOpenQualityIssueCount(count("""
                SELECT COUNT(*) FROM quality_issue
                WHERE project_id = ? AND assignee_id = ? AND deleted = 0
                  AND status NOT IN ('CLOSED', 'VOIDED')
                """, projectId, userId));
        impact.setPendingSealApprovalCount(count("""
                SELECT COUNT(*) FROM workflow_approval_task
                WHERE project_id = ? AND assignee_user_id = ?
                  AND business_code = 'SEAL_APPLICATION' AND status = 'PENDING'
                """, projectId, userId));
        impact.setSealApprovalConfigCount(count("""
                SELECT COUNT(*) FROM workflow_approval_config_user
                WHERE project_id = ? AND user_id = ?
                """, projectId, userId));
        return impact;
    }

    @Transactional
    public ResponsibilityImpactVO releaseAll(Long projectId, Long userId) {
        ResponsibilityImpactVO impact = impact(projectId, userId);
        clearElectrician(projectId, userId);
        clearSafetyManager(projectId, userId);
        clearInspectionReviewer(projectId, userId);
        clearRectification(projectId, userId);
        clearQuality(projectId, userId);
        removeSealApprovalConfiguration(projectId, userId);
        cancelSealApprovalTasksAndNotify(projectId, userId);
        return impact;
    }

    public ResponsibilityImpactVO releaseForCapabilityLoss(Long projectId, Long userId) {
        ResponsibilityImpactVO impact = impact(projectId, userId);
        if (!hasAny(userId, projectId,
                InspectionPermissionCodes.INSPECTION_DAILY_SUBMIT,
                SystemPermissionCodes.INSPECTION_SUBMIT)) {
            clearElectrician(projectId, userId);
        }
        if (!hasAny(userId, projectId,
                InspectionPermissionCodes.INSPECTION_REVIEW,
                SystemPermissionCodes.INSPECTION_MANAGE)) {
            clearSafetyManager(projectId, userId);
            clearInspectionReviewer(projectId, userId);
        }
        if (!hasAny(userId, projectId,
                SystemPermissionCodes.INSPECTION_RECTIFY)) {
            clearRectification(projectId, userId);
        }
        if (!permissionService.hasProjectPermission(userId, projectId,
                SystemPermissionCodes.QUALITY_RECTIFY)) {
            clearQuality(projectId, userId);
        }
        return impact;
    }

    private boolean hasAny(Long userId, Long projectId, String... codes) {
        for (String code : codes) {
            if (permissionService.hasProjectPermission(userId, projectId, code)) return true;
        }
        return false;
    }

    private void clearElectrician(Long projectId, Long userId) {
        jdbc.update("""
                UPDATE electric_box SET responsible_electrician_id = NULL,
                    responsible_electrician_name = NULL, update_time = NOW()
                WHERE project_id = ? AND responsible_electrician_id = ? AND deleted = 0
                """, projectId, userId);
    }

    private void clearSafetyManager(Long projectId, Long userId) {
        jdbc.update("""
                UPDATE electric_box SET safety_manager_id = NULL,
                    safety_manager_name = NULL, update_time = NOW()
                WHERE project_id = ? AND safety_manager_id = ? AND deleted = 0
                """, projectId, userId);
    }

    private void clearInspectionReviewer(Long projectId, Long userId) {
        jdbc.update("""
                UPDATE inspection_record SET assigned_reviewer_id = NULL,
                    assigned_reviewer_name = NULL, update_time = NOW()
                WHERE project_id = ? AND assigned_reviewer_id = ? AND deleted = 0
                  AND (review_time IS NULL OR status = 'REVIEW_PENDING')
                """, projectId, userId);
    }

    private void clearRectification(Long projectId, Long userId) {
        jdbc.update("""
                UPDATE inspection_rectification SET assignee_id = NULL,
                    assignee_name = NULL, update_time = NOW()
                WHERE project_id = ? AND assignee_id = ? AND deleted = 0 AND status <> 'CLOSED'
                """, projectId, userId);
    }

    private void clearQuality(Long projectId, Long userId) {
        jdbc.update("""
                UPDATE quality_issue SET assignee_id = NULL,
                    assignee_name = NULL, version = version + 1, update_time = NOW()
                WHERE project_id = ? AND assignee_id = ? AND deleted = 0
                  AND status NOT IN ('CLOSED', 'VOIDED')
                """, projectId, userId);
    }

    private void removeSealApprovalConfiguration(Long projectId, Long userId) {
        List<Long> configIds = jdbc.query("""
                SELECT DISTINCT config_id FROM workflow_approval_config_user
                WHERE project_id = ? AND user_id = ?
                ORDER BY config_id
                """, (rs, rowNum) -> rs.getLong(1), projectId, userId);
        if (configIds.isEmpty()) return;
        // Approval-config save locks the parent row before replacing its users.
        // Follow the same order here to avoid parent/child lock inversion.
        for (Long configId : configIds) {
            Long lockedId = jdbc.queryForObject("""
                    SELECT id FROM workflow_approval_config
                    WHERE id = ? FOR UPDATE
                    """, Long.class, configId);
            if (!Objects.equals(lockedId, configId)) {
                throw BusinessException.of(409, "审批配置状态已变化，请刷新后重试");
            }
        }
        int deleted = jdbc.update("""
                DELETE FROM workflow_approval_config_user
                WHERE project_id = ? AND user_id = ?
                """, projectId, userId);
        if (deleted < configIds.size()) {
            throw BusinessException.of(409, "审批配置状态已变化，请刷新后重试");
        }
        for (Long configId : configIds) {
            int updated = jdbc.update("""
                    UPDATE workflow_approval_config config
                    SET config.config_version = config.config_version + 1,
                        config.enabled = CASE WHEN EXISTS (
                            SELECT 1 FROM workflow_approval_config_user relation
                            WHERE relation.config_id = config.id AND relation.assignment_type = 'APPROVER'
                        ) THEN config.enabled ELSE 0 END,
                        config.update_time = NOW()
                    WHERE config.id = ?
                    """, configId);
            if (updated != 1) throw BusinessException.of(409, "审批配置状态已变化，请刷新后重试");
        }
    }

    private void cancelSealApprovalTasksAndNotify(Long projectId, Long userId) {
        List<Map<String, Object>> pendingTasks = jdbc.queryForList("""
                SELECT id, business_id FROM workflow_approval_task
                WHERE project_id = ? AND assignee_user_id = ?
                  AND business_code = 'SEAL_APPLICATION' AND status = 'PENDING'
                FOR UPDATE
                """, projectId, userId);
        if (pendingTasks.isEmpty()) return;
        int updated = jdbc.update("""
                UPDATE workflow_approval_task
                SET status = 'CANCELLED', decision_opinion = '审批人资格已失效，待管理员改派',
                    decision_time = NOW(), version = version + 1, update_time = NOW()
                WHERE project_id = ? AND assignee_user_id = ?
                  AND business_code = 'SEAL_APPLICATION' AND status = 'PENDING'
                """, projectId, userId);
        if (updated != pendingTasks.size()) {
            throw BusinessException.of(409, "用印审批任务状态已变化，请刷新后重试");
        }
        pendingTasks.stream()
                .map(row -> ((Number) row.get("business_id")).longValue())
                .filter(Objects::nonNull)
                .distinct()
                .filter(applicationId -> count("""
                        SELECT COUNT(*) FROM workflow_approval_task
                        WHERE business_code = 'SEAL_APPLICATION' AND business_id = ? AND status = 'PENDING'
                        """, applicationId) == 0)
                .forEach(applicationId -> notifyReassignRequired(projectId, applicationId, userId));
    }

    private void notifyReassignRequired(Long projectId, Long applicationId, Long removedUserId) {
        List<Map<String, Object>> applications = jdbc.queryForList("""
                SELECT application_no, seal_name FROM seal_application
                WHERE id = ? AND project_id = ? AND status = 'PENDING_APPROVAL' AND deleted = 0
                """, applicationId, projectId);
        if (applications.isEmpty()) return;
        Map<String, Object> application = applications.get(0);
        String applicationNo = Objects.toString(application.get("application_no"), "待编号申请");
        String sealName = Objects.toString(application.get("seal_name"), "用印申请");
        List<Long> administratorIds = jdbc.query("""
                SELECT DISTINCT user.id
                FROM sys_user user
                INNER JOIN sys_user_role relation ON relation.user_id = user.id
                INNER JOIN sys_role role ON role.id = relation.role_id
                WHERE role.role_code = 'PLATFORM_ADMIN'
                  AND role.enabled = 1 AND role.deleted = 0
                  AND user.status = 1 AND user.deleted = 0
                """, (rs, rowNum) -> rs.getLong(1));
        for (Long administratorId : administratorIds) {
            notificationService.notify(administratorId, projectId, "SEAL_APPLICATION", applicationId,
                    "SEAL_REASSIGN_REQUIRED", "用印申请待改派：" + sealName,
                    applicationNo + " 的原审批人资格已失效，请重新指派审批人",
                    "seal:reassign-required:" + applicationId + ":" + removedUserId + ":" + administratorId);
        }
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }
}
