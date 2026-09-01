package com.example.siteplatform.quality.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.quality.dto.QualityWeeklyReminderSettingRequest;
import com.example.siteplatform.quality.entity.QualityWeeklyReminderSetting;
import com.example.siteplatform.quality.mapper.QualityAssigneeMapper;
import com.example.siteplatform.quality.mapper.QualityWeeklyReminderSettingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualityWeeklyReminderSettingServiceTest {

    @Mock private QualityWeeklyReminderSettingMapper settingMapper;
    @Mock private QualityAssigneeMapper assigneeMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private ProjectPermissionService projectPermissionService;
    @Mock private OperationLogMapper operationLogMapper;

    private QualityWeeklyReminderSettingService service;
    private SysUser manager;

    @BeforeEach
    void setUp() {
        service = new QualityWeeklyReminderSettingService();
        ReflectionTestUtils.setField(service, "settingMapper", settingMapper);
        ReflectionTestUtils.setField(service, "assigneeMapper", assigneeMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "projectPermissionService", projectPermissionService);
        ReflectionTestUtils.setField(service, "operationLogMapper", operationLogMapper);
        manager = user(99L, "质量经理", 1, 0);
        lenient().when(projectPermissionService.canManageQuality(99L, 9L)).thenReturn(true);
    }

    @Test
    void absentSettingReturnsDisabledSundayAtSixWithoutWritingDatabase() {
        var result = service.getSetting(9L, manager);

        assertFalse(result.getEnabled());
        assertEquals(7, result.getDayOfWeek());
        assertEquals(LocalTime.of(18, 0), result.getTriggerTime());
        assertEquals(0, result.getVersion());
        assertNull(result.getReminderEffectiveTime());
        assertNull(result.getNextReminderTime());
        verify(settingMapper, never()).insert(any());
    }

    @Test
    void enablingNewRuleStoresEffectiveTimeAndOnlySchedulesAfterIt() {
        SysUser responsible = user(7L, "周检责任人", 1, 0);
        when(userMapper.selectById(7L)).thenReturn(responsible);
        when(projectPermissionService.getProjectAccessStatus(7L, 9L)).thenReturn("ACTIVE");
        when(projectPermissionService.canManageQuality(7L, 9L)).thenReturn(true);
        when(settingMapper.insert(any())).thenAnswer(invocation -> {
            QualityWeeklyReminderSetting inserted = invocation.getArgument(0);
            inserted.setId(21L);
            return 1;
        });
        when(operationLogMapper.insert(any())).thenReturn(1);

        var result = service.updateSetting(9L, request(true, 7L, 0), manager);

        assertTrue(result.getEnabled());
        assertEquals(1, result.getVersion());
        assertEquals("周检责任人", result.getResponsibleUserName());
        assertNotNull(result.getReminderEffectiveTime());
        assertTrue(result.getNextReminderTime().isAfter(result.getReminderEffectiveTime()));
        assertEquals(DayOfWeek.SUNDAY, result.getNextReminderTime().getDayOfWeek());
        assertEquals(LocalTime.of(18, 0), result.getNextReminderTime().toLocalTime());
        ArgumentCaptor<QualityWeeklyReminderSetting> inserted =
                ArgumentCaptor.forClass(QualityWeeklyReminderSetting.class);
        verify(settingMapper).insert(inserted.capture());
        assertEquals(result.getReminderEffectiveTime(), inserted.getValue().getEffectiveTime());
    }

    @Test
    void occurrenceAtEffectiveTimeIsNotEligibleButLaterOccurrenceIs() {
        QualityWeeklyReminderSetting setting = enabledSetting();
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime scheduled = service.scheduledAt(setting, monday);
        setting.setEffectiveTime(scheduled);

        assertFalse(service.isOccurrenceEffective(setting, scheduled));
        assertTrue(service.isOccurrenceEffective(setting, scheduled.plusWeeks(1)));
    }

    @Test
    void enablingBeforeThisWeeksTriggerStillStartsNextNaturalWeek() {
        QualityWeeklyReminderSetting setting = enabledSetting();
        LocalDate monday = LocalDate.of(2026, 8, 24);
        LocalDateTime thisWeekTrigger = service.scheduledAt(setting, monday);
        setting.setEffectiveTime(LocalDateTime.of(2026, 8, 25, 9, 0));

        assertFalse(service.isOccurrenceEffective(setting, thisWeekTrigger));
        assertTrue(service.isOccurrenceEffective(setting, thisWeekTrigger.plusWeeks(1)));
        assertEquals(thisWeekTrigger.plusWeeks(1),
                service.nextScheduledAt(setting, LocalDateTime.of(2026, 8, 25, 9, 0)));
    }

    @Test
    void staleVersionReturns409BeforeAnyWrite() {
        QualityWeeklyReminderSetting existing = enabledSetting();
        existing.setId(21L);
        existing.setProjectId(9L);
        existing.setVersion(4);
        when(settingMapper.selectByProjectIdForUpdate(9L)).thenReturn(existing);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateSetting(9L, request(false, 7L, 3), manager));

        assertEquals(409, error.getCode());
        verify(settingMapper, never()).updateSetting(any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void reminderCandidatesMustBeActiveProjectQualityManagers() {
        SysUser eligible = user(7L, "合格责任人", 1, 0);
        SysUser noManage = user(8L, "无管理权限", 1, 0);
        SysUser disabled = user(10L, "停用账号", 0, 0);
        when(assigneeMapper.selectPotentialAssignees(9L))
                .thenReturn(List.of(eligible, noManage, disabled));
        when(projectPermissionService.getProjectAccessStatus(7L, 9L)).thenReturn("ACTIVE");
        when(projectPermissionService.getProjectAccessStatus(8L, 9L)).thenReturn("ACTIVE");
        when(projectPermissionService.canManageQuality(7L, 9L)).thenReturn(true);
        when(projectPermissionService.canManageQuality(8L, 9L)).thenReturn(false);

        var result = service.listReminderAssignees(9L, manager);

        assertEquals(List.of(7L), result.stream().map(item -> item.getUserId()).toList());
        assertEquals("合格责任人", result.get(0).getDisplayName());
    }

    @Test
    void disablingExistingRuleClearsEffectiveTimeAndIncrementsVersion() {
        QualityWeeklyReminderSetting existing = enabledSetting();
        existing.setId(21L);
        existing.setProjectId(9L);
        existing.setVersion(4);
        existing.setResponsibleUserId(7L);
        existing.setResponsibleUserName("周检责任人");
        when(settingMapper.selectByProjectIdForUpdate(9L)).thenReturn(existing);
        when(userMapper.selectById(7L)).thenReturn(user(7L, "周检责任人", 1, 0));
        when(projectPermissionService.getProjectAccessStatus(7L, 9L)).thenReturn("ACTIVE");
        when(projectPermissionService.canManageQuality(7L, 9L)).thenReturn(true);
        when(settingMapper.updateSetting(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any())).thenReturn(1);
        when(operationLogMapper.insert(any())).thenReturn(1);

        var result = service.updateSetting(9L, request(false, 7L, 4), manager);

        assertFalse(result.getEnabled());
        assertEquals(5, result.getVersion());
        assertNull(result.getReminderEffectiveTime());
        assertNull(result.getNextReminderTime());
    }

    private QualityWeeklyReminderSettingRequest request(boolean enabled,
                                                        Long responsibleUserId,
                                                        int expectedVersion) {
        QualityWeeklyReminderSettingRequest request = new QualityWeeklyReminderSettingRequest();
        request.setEnabled(enabled);
        request.setDayOfWeek(7);
        request.setTriggerTime(LocalTime.of(18, 0));
        request.setResponsibleUserId(responsibleUserId);
        request.setExpectedVersion(expectedVersion);
        return request;
    }

    private QualityWeeklyReminderSetting enabledSetting() {
        QualityWeeklyReminderSetting setting = new QualityWeeklyReminderSetting();
        setting.setEnabled(1);
        setting.setDayOfWeek(7);
        setting.setTriggerTime(LocalTime.of(18, 0));
        setting.setEffectiveTime(LocalDateTime.now().minusWeeks(1));
        return setting;
    }

    private SysUser user(Long id, String name, int status, int deleted) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("user_" + id);
        user.setRealName(name);
        user.setStatus(status);
        user.setDeleted(deleted);
        return user;
    }
}
