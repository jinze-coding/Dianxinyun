package com.example.siteplatform.inspection.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.electricbox.service.ElectricBoxInspectionScopeService;
import com.example.siteplatform.inspection.entity.ProjectInspectionSetting;
import com.example.siteplatform.inspection.general.dto.GeneralInspectionPlanConfig;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionPlan;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionTask;
import com.example.siteplatform.inspection.general.mapper.GeneralInspectionPlanMapper;
import com.example.siteplatform.inspection.general.mapper.GeneralInspectionTaskMapper;
import com.example.siteplatform.inspection.mapper.InspectionRecordMapper;
import com.example.siteplatform.inspection.mapper.ProjectInspectionSettingMapper;
import com.example.siteplatform.notification.service.UserNotificationService;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InspectionSubmissionReminderServiceTest {

    @Mock private ProjectInspectionSettingMapper settingMapper;
    @Mock private ElectricBoxMapper electricBoxMapper;
    @Mock private ElectricBoxInspectionScopeService scopeService;
    @Mock private InspectionRecordMapper inspectionRecordMapper;
    @Mock private GeneralInspectionTaskMapper taskMapper;
    @Mock private GeneralInspectionPlanMapper planMapper;
    @Mock private ProjectInfoMapper projectMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private ProjectPermissionService permissionService;
    @Mock private UserNotificationService notificationService;

    private InspectionSubmissionReminderService service;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        service = new InspectionSubmissionReminderService(settingMapper, electricBoxMapper, scopeService,
                inspectionRecordMapper, taskMapper, planMapper, projectMapper, userMapper,
                permissionService, notificationService, objectMapper);
    }

    @Test
    void electricReminderLocksAndRechecksBeforeNotifyingOwner() {
        LocalDate date = LocalDate.of(2026, 8, 29);
        LocalDateTime now = LocalDateTime.of(2026, 8, 29, 18, 1);
        when(settingMapper.selectByProjectIdForUpdate(1L)).thenReturn(electricSetting());
        when(projectMapper.selectById(1L)).thenReturn(project(1L));
        ElectricBox box = box();
        when(electricBoxMapper.selectByIdForUpdate(10L)).thenReturn(box);
        when(scopeService.isRequired(box, date)).thenReturn(true);
        when(inspectionRecordMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectById(9L)).thenReturn(activeUser(9L));
        when(permissionService.getProjectAccessStatus(9L, 1L)).thenReturn("ACTIVE");
        when(permissionService.hasSystemPermission(9L, 1L, SystemPermissionCodes.INSPECTION_SUBMIT)).thenReturn(true);
        when(permissionService.hasInspectionPermission(9L, 1L,
                InspectionPermissionCodes.INSPECTION_DAILY_SUBMIT)).thenReturn(true);

        assertTrue(service.remindElectricBox(1L, 10L, date, now));

        verify(notificationService).notify(9L, 1L, "ELECTRIC_BOX_INSPECTION", 10L,
                "ELECTRIC_DAILY_NOT_SUBMITTED", "电箱日检未提交",
                "DX-01 今日巡检记录尚未提交，请及时完成。",
                "reminder:ebox:10:2026-08-29", "INSPECTION_FORM", "{\"boxId\":10}");
    }

    @Test
    void electricExistingRecordOrInvalidOwnerIsSkippedWithoutBroadcast() {
        LocalDate date = LocalDate.of(2026, 8, 29);
        LocalDateTime now = LocalDateTime.of(2026, 8, 29, 18, 1);
        when(settingMapper.selectByProjectIdForUpdate(1L)).thenReturn(electricSetting());
        when(projectMapper.selectById(1L)).thenReturn(project(1L));
        ElectricBox box = box();
        when(electricBoxMapper.selectByIdForUpdate(10L)).thenReturn(box);
        when(scopeService.isRequired(box, date)).thenReturn(true);
        when(inspectionRecordMapper.selectCount(any())).thenReturn(1L);

        assertFalse(service.remindElectricBox(1L, 10L, date, now));

        verifyNoInteractions(notificationService);
    }

    @Test
    void electricOccurrenceAtOrBeforeActivationBoundaryIsNotBackfilled() {
        ProjectInspectionSetting setting = electricSetting();
        setting.setReminderEffectiveTime(LocalDateTime.of(2026, 8, 29, 18, 0));
        when(settingMapper.selectByProjectIdForUpdate(1L)).thenReturn(setting);

        assertFalse(service.remindElectricBox(1L, 10L, LocalDate.of(2026, 8, 29),
                LocalDateTime.of(2026, 8, 29, 18, 1)));

        verify(electricBoxMapper, never()).selectByIdForUpdate(anyLong());
        verifyNoInteractions(notificationService);
    }

    @Test
    void enablingBeforeTodaysCutoffStillStartsOnNextBoxDay() {
        ProjectInspectionSetting setting = electricSetting();
        setting.setReminderEffectiveTime(LocalDateTime.of(2026, 8, 29, 10, 0));
        when(settingMapper.selectByProjectIdForUpdate(1L)).thenReturn(setting);

        assertFalse(service.remindElectricBox(1L, 10L, LocalDate.of(2026, 8, 29),
                LocalDateTime.of(2026, 8, 29, 18, 1)));

        verify(electricBoxMapper, never()).selectByIdForUpdate(anyLong());
        verifyNoInteractions(notificationService);
    }

    @Test
    void edgeReminderUsesTaskOwnerAndDeterministicDedupKey() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 8, 29, 18, 1);
        GeneralInspectionTask task = edgeTask();
        GeneralInspectionPlan plan = edgePlan();
        plan.setDraftConfigJson(objectMapper.writeValueAsString(edgeConfig(
                LocalDateTime.of(2026, 8, 29, 10, 0))));
        when(taskMapper.selectByIdForUpdate(20L)).thenReturn(task);
        when(planMapper.selectByIdForUpdate(30L)).thenReturn(plan);
        when(projectMapper.selectById(1L)).thenReturn(project(1L));
        when(userMapper.selectById(9L)).thenReturn(activeUser(9L));
        when(permissionService.getProjectAccessStatus(9L, 1L)).thenReturn("ACTIVE");
        when(permissionService.hasInspectionPermission(9L, 1L,
                InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT)).thenReturn(true);

        assertTrue(service.remindEdgeTask(20L, now));

        verify(notificationService).notify(9L, 1L, "EDGE_INSPECTION_TASK", 20L,
                "EDGE_INSPECTION_NOT_SUBMITTED", "临边巡检未提交",
                "教学楼屋面临边 已到截止时间，巡检记录尚未提交，请及时处理。",
                "reminder:edge:20", "EDGE_INSPECTION_TASK_DETAIL", "{\"taskId\":20}");
    }

    @Test
    void edgeTaskDueBeforeReminderActivationIsHistoricalAndSkipped() throws Exception {
        GeneralInspectionTask task = edgeTask();
        GeneralInspectionPlan plan = edgePlan();
        plan.setDraftConfigJson(objectMapper.writeValueAsString(edgeConfig(
                LocalDateTime.of(2026, 8, 29, 18, 0))));
        when(taskMapper.selectByIdForUpdate(20L)).thenReturn(task);
        when(planMapper.selectByIdForUpdate(30L)).thenReturn(plan);

        assertFalse(service.remindEdgeTask(20L, LocalDateTime.of(2026, 8, 29, 18, 1)));

        verifyNoInteractions(notificationService);
    }

    private ProjectInspectionSetting electricSetting() {
        ProjectInspectionSetting setting = new ProjectInspectionSetting();
        setting.setProjectId(1L);
        setting.setDailyCutoffTime(java.time.LocalTime.of(18, 0));
        setting.setSubmissionReminderEnabled(1);
        setting.setReminderEffectiveTime(LocalDateTime.of(2026, 8, 28, 10, 0));
        return setting;
    }

    private ElectricBox box() {
        ElectricBox box = new ElectricBox();
        box.setId(10L);
        box.setProjectId(1L);
        box.setBoxCode("DX-01");
        box.setStatus("ACTIVE");
        box.setResponsibleElectricianId(9L);
        return box;
    }

    private GeneralInspectionTask edgeTask() {
        GeneralInspectionTask task = new GeneralInspectionTask();
        task.setId(20L);
        task.setProjectId(1L);
        task.setPlanId(30L);
        task.setPointTypeCode("ROOF_EDGE");
        task.setPointName("教学楼屋面临边");
        task.setStatus("PENDING");
        task.setDueTime(LocalDateTime.of(2026, 8, 29, 18, 0));
        task.setAssigneeId(9L);
        return task;
    }

    private GeneralInspectionPlan edgePlan() {
        GeneralInspectionPlan plan = new GeneralInspectionPlan();
        plan.setId(30L);
        plan.setProjectId(1L);
        plan.setPlanCode("EDGE_PROJECT_SCHEDULE");
        plan.setStatus("PUBLISHED");
        plan.setDeleted(0);
        return plan;
    }

    private GeneralInspectionPlanConfig edgeConfig(LocalDateTime effectiveTime) {
        GeneralInspectionPlanConfig config = new GeneralInspectionPlanConfig();
        config.setSubmissionReminderEnabled(true);
        config.setReminderEffectiveTime(effectiveTime);
        return config;
    }

    private SysUser activeUser(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(1);
        user.setDeleted(0);
        return user;
    }

    private ProjectInfo project(Long id) {
        ProjectInfo project = new ProjectInfo();
        project.setId(id);
        project.setDeleted(0);
        return project;
    }
}
