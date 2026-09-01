package com.example.siteplatform.inspection.general.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.file.service.FileResourceService;
import com.example.siteplatform.inspection.general.dto.EdgeInspectionRectificationCompleteRequest;
import com.example.siteplatform.inspection.general.dto.EdgeInspectionRectificationReassignRequest;
import com.example.siteplatform.inspection.general.dto.EdgeInspectionRectificationReviewRequest;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionActionLog;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionRectification;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionTask;
import com.example.siteplatform.inspection.general.mapper.*;
import com.example.siteplatform.notification.service.UserNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GeneralInspectionRectificationSheetServiceTest {

    private GeneralInspectionTaskMapper taskMapper;
    private GeneralInspectionTaskItemMapper taskItemMapper;
    private GeneralInspectionRectificationMapper rectificationMapper;
    private GeneralInspectionActionLogMapper actionLogMapper;
    private FileResourceService fileResourceService;
    private GeneralInspectionPermissionService permissionService;
    private SysUserMapper userMapper;
    private UserNotificationService notificationService;
    private GeneralInspectionEventPublisher eventPublisher;
    private GeneralInspectionTaskService service;
    private SysUser user;

    @BeforeEach
    void setUp() {
        taskMapper = mock(GeneralInspectionTaskMapper.class);
        taskItemMapper = mock(GeneralInspectionTaskItemMapper.class);
        rectificationMapper = mock(GeneralInspectionRectificationMapper.class);
        actionLogMapper = mock(GeneralInspectionActionLogMapper.class);
        fileResourceService = mock(FileResourceService.class);
        permissionService = mock(GeneralInspectionPermissionService.class);
        userMapper = mock(SysUserMapper.class);
        notificationService = mock(UserNotificationService.class);
        eventPublisher = mock(GeneralInspectionEventPublisher.class);
        service = new GeneralInspectionTaskService(
                permissionService,
                taskMapper,
                taskItemMapper,
                mock(GeneralInspectionPointMapper.class),
                mock(GeneralInspectionTemplateVersionMapper.class),
                rectificationMapper,
                actionLogMapper,
                userMapper,
                fileResourceService,
                notificationService,
                eventPublisher,
                new ObjectMapper());
        user = new SysUser();
        user.setId(9L);
        user.setUsername("rectifier");
        when(taskItemMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(taskMapper.updateById(any())).thenReturn(1);
        when(rectificationMapper.updateById(any())).thenReturn(1);
        when(actionLogMapper.insert(any())).thenReturn(1);
    }

    @Test
    void rejectedSheetCanResubmitHistoricalPhotoAndBindOnlyNewPhoto() {
        stubRejectedSheet();

        service.completeEdgeRectificationSheet(21L, request(List.of(101L, 102L)), user);

        verify(fileResourceService).validateAndBind(eq(user), eq(2L), eq(List.of(102L)),
                eq("EDGE_INSPECTION_RECTIFICATION_PENDING"),
                eq("EDGE_INSPECTION_RECTIFICATION"), eq(31L));
        assertThat(rectificationMapper.selectByTaskIdForUpdate(21L).get(0).getRectificationPhotoFileIds())
                .isEqualTo("101,102");
        ArgumentCaptor<GeneralInspectionActionLog> logCaptor =
                ArgumentCaptor.forClass(GeneralInspectionActionLog.class);
        verify(actionLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getFromStatus()).isEqualTo("REJECTED");
        assertThat(logCaptor.getValue().getToStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void rejectedSheetCanResubmitWithHistoricalPhotoOnly() {
        stubRejectedSheet();

        service.completeEdgeRectificationSheet(21L, request(List.of(101L)), user);

        verify(fileResourceService, never()).validateAndBind(any(), anyLong(), anyList(),
                anyString(), anyString(), anyLong());
    }

    @Test
    void reviewRejectionClearsRectifierWhoseQualificationWasRevokedAndCreatesAssignmentEvent() {
        GeneralInspectionTask task = new GeneralInspectionTask();
        task.setId(21L);
        task.setProjectId(2L);
        task.setPointTypeCode("ROOF_EDGE");
        task.setPointTypeName("屋面边");
        task.setPointName("1号屋面");
        task.setStatus("RECTIFICATION_PENDING");
        task.setDefaultRectifierId(7L);
        task.setDefaultRectifierName("已失权整改人");
        task.setReviewerId(9L);
        task.setVersion(3);
        when(taskMapper.selectByIdForUpdate(21L)).thenReturn(task);

        GeneralInspectionRectification rectification = new GeneralInspectionRectification();
        rectification.setId(31L);
        rectification.setProjectId(2L);
        rectification.setTaskId(21L);
        rectification.setTaskItemId(41L);
        rectification.setPointName("1号屋面");
        rectification.setItemName("周边防护完整");
        rectification.setAssigneeId(7L);
        rectification.setAssigneeName("已失权整改人");
        rectification.setReviewerId(9L);
        rectification.setStatus("COMPLETED");
        rectification.setVersion(2);
        when(rectificationMapper.selectByTaskIdForUpdate(21L)).thenReturn(List.of(rectification));
        when(permissionService.hasActiveProjectAccess(2L, 7L)).thenReturn(false);

        EdgeInspectionRectificationReviewRequest request = new EdgeInspectionRectificationReviewRequest();
        request.setExpectedVersion(3);
        request.setComment("整改仍不到位");
        service.reviewEdgeRectificationSheet(21L, request, false, user);

        assertThat(rectification.getStatus()).isEqualTo("UNASSIGNED");
        assertThat(rectification.getAssigneeId()).isNull();
        assertThat(rectification.getAssigneeName()).isNull();
        assertThat(rectification.getRejectCount()).isEqualTo(1);
        assertThat(task.getDefaultRectifierId()).isNull();
        assertThat(task.getDefaultRectifierName()).isNull();
        verifyNoInteractions(notificationService);
        ArgumentCaptor<GeneralInspectionDomainEvent> eventCaptor =
                ArgumentCaptor.forClass(GeneralInspectionDomainEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("RECTIFICATION_UNASSIGNED");
        assertThat(eventCaptor.getValue().businessId()).isEqualTo(21L);

        ArgumentCaptor<GeneralInspectionActionLog> logCaptor =
                ArgumentCaptor.forClass(GeneralInspectionActionLog.class);
        verify(actionLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getFromStatus()).isEqualTo("COMPLETED");
        assertThat(logCaptor.getValue().getToStatus()).isEqualTo("UNASSIGNED");
    }

    @Test
    void sheetReassignmentAuditKeepsBeforeAndAfterPersonnelSnapshots() {
        GeneralInspectionTask task = new GeneralInspectionTask();
        task.setId(21L);
        task.setProjectId(2L);
        task.setPointTypeCode("ROOF_EDGE");
        task.setPointName("1号屋面");
        task.setStatus("RECTIFICATION_PENDING");
        task.setDefaultRectifierId(7L);
        task.setDefaultRectifierName("原整改人");
        task.setReviewerId(8L);
        task.setReviewerName("原复查人");
        task.setVersion(3);
        when(taskMapper.selectByIdForUpdate(21L)).thenReturn(task);

        GeneralInspectionRectification rectification = new GeneralInspectionRectification();
        rectification.setId(31L);
        rectification.setProjectId(2L);
        rectification.setTaskId(21L);
        rectification.setTaskItemId(41L);
        rectification.setAssigneeId(7L);
        rectification.setAssigneeName("原整改人");
        rectification.setReviewerId(8L);
        rectification.setReviewerName("原复查人");
        rectification.setStatus("COMPLETED");
        rectification.setVersion(2);
        when(rectificationMapper.selectByTaskIdForUpdate(21L)).thenReturn(List.of(rectification));

        SysUser newRectifier = activeUser(10L, "新整改人");
        SysUser newReviewer = activeUser(11L, "新复查人");
        when(permissionService.hasActiveProjectAccess(2L, 10L)).thenReturn(true);
        when(permissionService.hasActiveProjectAccess(2L, 11L)).thenReturn(true);
        when(permissionService.hasInspectionPermission(2L, 10L, "EDGE_INSPECTION_RECTIFY")).thenReturn(true);
        when(permissionService.hasInspectionPermission(2L, 11L, "EDGE_INSPECTION_REVIEW")).thenReturn(true);
        when(userMapper.selectById(10L)).thenReturn(newRectifier);
        when(userMapper.selectById(11L)).thenReturn(newReviewer);

        EdgeInspectionRectificationReassignRequest request = new EdgeInspectionRectificationReassignRequest();
        request.setExpectedVersion(3);
        request.setAssigneeId(10L);
        request.setReviewerId(11L);
        request.setReason("人员调整");
        service.reassignEdgeRectificationSheet(21L, request, user);

        ArgumentCaptor<GeneralInspectionActionLog> logCaptor =
                ArgumentCaptor.forClass(GeneralInspectionActionLog.class);
        verify(actionLogMapper).insert(logCaptor.capture());
        GeneralInspectionActionLog log = logCaptor.getValue();
        assertThat(log.getBeforeJson()).contains("\"assigneeId\":7", "\"reviewerId\":8");
        assertThat(log.getAfterJson()).contains("\"assigneeId\":10", "\"reviewerId\":11");
        assertThat(log.getComment()).isEqualTo("人员调整");
    }

    private void stubRejectedSheet() {
        GeneralInspectionTask task = new GeneralInspectionTask();
        task.setId(21L);
        task.setProjectId(2L);
        task.setPointTypeCode("ROOF_EDGE");
        task.setPointTypeName("屋面边");
        task.setPointName("1号屋面");
        task.setStatus("RECTIFICATION_PENDING");
        task.setReviewerId(10L);
        task.setVersion(3);
        when(taskMapper.selectByIdForUpdate(21L)).thenReturn(task);

        GeneralInspectionRectification rectification = new GeneralInspectionRectification();
        rectification.setId(31L);
        rectification.setProjectId(2L);
        rectification.setTaskId(21L);
        rectification.setTaskItemId(41L);
        rectification.setPointName("1号屋面");
        rectification.setItemName("周边防护完整");
        rectification.setAssigneeId(9L);
        rectification.setReviewerId(10L);
        rectification.setStatus("REJECTED");
        rectification.setRectificationPhotoFileIds("101");
        rectification.setVersion(2);
        when(rectificationMapper.selectByTaskIdForUpdate(21L)).thenReturn(List.of(rectification));
    }

    private EdgeInspectionRectificationCompleteRequest request(List<Long> photos) {
        EdgeInspectionRectificationCompleteRequest request = new EdgeInspectionRectificationCompleteRequest();
        request.setExpectedVersion(3);
        EdgeInspectionRectificationCompleteRequest.ItemFeedback item =
                new EdgeInspectionRectificationCompleteRequest.ItemFeedback();
        item.setRectificationId(31L);
        item.setExpectedVersion(2);
        item.setFeedback("已恢复防护");
        item.setPhotoFileIds(photos);
        request.setItems(List.of(item));
        return request;
    }

    private SysUser activeUser(Long id, String name) {
        SysUser candidate = new SysUser();
        candidate.setId(id);
        candidate.setUsername(name);
        candidate.setRealName(name);
        candidate.setStatus(1);
        candidate.setDeleted(0);
        return candidate;
    }
}
