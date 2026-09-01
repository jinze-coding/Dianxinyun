package com.example.siteplatform.inspection.general.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.inspection.general.mapper.EdgeInspectionWorkspaceMapper;
import com.example.siteplatform.inspection.general.vo.EdgeInspectionWorkspaceSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class EdgeInspectionWorkspaceService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final GeneralInspectionPermissionService permissionService;
    private final EdgeInspectionWorkspaceMapper workspaceMapper;

    @Transactional(readOnly = true)
    public EdgeInspectionWorkspaceSummaryVO summary(Long projectId, SysUser currentUser) {
        permissionService.requireAnyEdgePermission(projectId, currentUser);
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        EdgeInspectionWorkspaceSummaryVO summary = workspaceMapper.selectSummary(
                projectId, currentUser.getId(), now.toLocalDate(), now);
        return summary == null ? emptySummary() : summary;
    }

    private EdgeInspectionWorkspaceSummaryVO emptySummary() {
        EdgeInspectionWorkspaceSummaryVO summary = new EdgeInspectionWorkspaceSummaryVO();
        summary.setEnabledPointCount(0L);
        summary.setMyTodayDueCount(0L);
        summary.setMyTodayPendingCount(0L);
        summary.setMyTodaySubmittedCount(0L);
        summary.setMyTodayCancelledCount(0L);
        summary.setMyOverdueCount(0L);
        summary.setMyRectificationPendingCount(0L);
        summary.setMyReviewPendingCount(0L);
        return summary;
    }
}
