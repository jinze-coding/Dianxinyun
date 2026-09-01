package com.example.siteplatform.inspection.general.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.inspection.general.mapper.EdgeInspectionWorkspaceMapper;
import com.example.siteplatform.inspection.general.vo.EdgeInspectionWorkspaceSummaryVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdgeInspectionWorkspaceServiceTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Mock private GeneralInspectionPermissionService permissionService;
    @Mock private EdgeInspectionWorkspaceMapper workspaceMapper;

    private EdgeInspectionWorkspaceService service;
    private SysUser user;

    @BeforeEach
    void setUp() {
        service = new EdgeInspectionWorkspaceService(permissionService, workspaceMapper);
        user = new SysUser();
        user.setId(9L);
    }

    @Test
    void summaryUsesShanghaiBusinessTimeAndReturnsAggregate() {
        EdgeInspectionWorkspaceSummaryVO expected = summary(8L, 8L, 6L, 1L, 1L, 2L, 1L, 1L);
        when(workspaceMapper.selectSummary(eq(3L), eq(9L), any(LocalDate.class),
                any(LocalDateTime.class))).thenReturn(expected);
        LocalDateTime before = LocalDateTime.now(BUSINESS_ZONE);

        EdgeInspectionWorkspaceSummaryVO actual = service.summary(3L, user);

        LocalDateTime after = LocalDateTime.now(BUSINESS_ZONE);
        assertThat(actual).isSameAs(expected);
        verify(permissionService).requireAnyEdgePermission(3L, user);
        ArgumentCaptor<LocalDate> today = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(workspaceMapper).selectSummary(eq(3L), eq(9L), today.capture(), now.capture());
        assertThat(now.getValue()).isBetween(before, after);
        assertThat(today.getValue()).isEqualTo(now.getValue().toLocalDate());
    }

    @Test
    void nullAggregateFallsBackToEightExplicitZeroFields() throws Exception {
        when(workspaceMapper.selectSummary(eq(3L), eq(9L), any(LocalDate.class),
                any(LocalDateTime.class))).thenReturn(null);

        EdgeInspectionWorkspaceSummaryVO result = service.summary(3L, user);

        assertThat(new ObjectMapper().valueToTree(result).propertyStream()
                .map(entry -> entry.getKey()).collect(java.util.stream.Collectors.toSet()))
                .isEqualTo(Set.of(
                        "enabledPointCount",
                        "myTodayDueCount",
                        "myTodayPendingCount",
                        "myTodaySubmittedCount",
                        "myTodayCancelledCount",
                        "myOverdueCount",
                        "myRectificationPendingCount",
                        "myReviewPendingCount"));
        assertThat(new ObjectMapper().valueToTree(result).propertyStream()
                .allMatch(entry -> entry.getValue().longValue() == 0L)).isTrue();
    }

    @Test
    void permissionFailureStopsBeforeReadingWorkspaceData() {
        BusinessException forbidden = BusinessException.forbidden("无临边巡检访问权限");
        org.mockito.Mockito.doThrow(forbidden).when(permissionService)
                .requireAnyEdgePermission(3L, user);

        assertThatThrownBy(() -> service.summary(3L, user)).isSameAs(forbidden);
        verify(workspaceMapper, never()).selectSummary(any(), any(), any(), any());
    }

    private EdgeInspectionWorkspaceSummaryVO summary(Long enabledPoints, Long todayDue, Long todayPending,
                                                       Long todaySubmitted, Long todayCancelled, Long overdue,
                                                       Long rectify, Long review) {
        EdgeInspectionWorkspaceSummaryVO summary = new EdgeInspectionWorkspaceSummaryVO();
        summary.setEnabledPointCount(enabledPoints);
        summary.setMyTodayDueCount(todayDue);
        summary.setMyTodayPendingCount(todayPending);
        summary.setMyTodaySubmittedCount(todaySubmitted);
        summary.setMyTodayCancelledCount(todayCancelled);
        summary.setMyOverdueCount(overdue);
        summary.setMyRectificationPendingCount(rectify);
        summary.setMyReviewPendingCount(review);
        return summary;
    }
}
