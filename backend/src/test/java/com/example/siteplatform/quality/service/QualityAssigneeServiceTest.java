package com.example.siteplatform.quality.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.quality.entity.QualityIssue;
import com.example.siteplatform.quality.mapper.QualityAssigneeMapper;
import com.example.siteplatform.quality.mapper.QualityIssueMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualityAssigneeServiceTest {

    @Mock private QualityAssigneeMapper assigneeMapper;
    @Mock private QualityIssueMapper issueMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private ProjectPermissionService projectPermissionService;

    private QualityAssigneeService service;
    private SysUser currentUser;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), QualityIssueMapper.class.getName()),
                QualityIssue.class);
        service = new QualityAssigneeService();
        ReflectionTestUtils.setField(service, "assigneeMapper", assigneeMapper);
        ReflectionTestUtils.setField(service, "issueMapper", issueMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "projectPermissionService", projectPermissionService);
        currentUser = user(99L, 1, 0);
    }

    @Test
    void candidatesRequireActiveProjectAccessAndBothQualityPermissions() {
        SysUser eligibleMember = user(1L, 1, 0);
        eligibleMember.setRealName("整改成员");
        SysUser disabledAccessMember = user(2L, 1, 0);
        SysUser platformAdministrator = user(3L, 1, 0);
        platformAdministrator.setRealName("平台管理员");
        when(assigneeMapper.selectPotentialAssignees(9L))
                .thenReturn(List.of(eligibleMember, disabledAccessMember, platformAdministrator));
        when(projectPermissionService.getProjectAccessStatus(1L, 9L)).thenReturn("ACTIVE");
        when(projectPermissionService.getProjectAccessStatus(2L, 9L)).thenReturn("DISABLED");
        // 平台管理员按现有全局项目访问模型同样返回 ACTIVE，不强制补 sys_user_project。
        when(projectPermissionService.getProjectAccessStatus(3L, 9L)).thenReturn("ACTIVE");
        when(projectPermissionService.hasSystemPermission(
                1L, 9L, SystemPermissionCodes.QUALITY_VIEW)).thenReturn(true);
        when(projectPermissionService.hasSystemPermission(
                1L, 9L, SystemPermissionCodes.QUALITY_RECTIFY)).thenReturn(true);
        when(projectPermissionService.hasSystemPermission(
                3L, 9L, SystemPermissionCodes.QUALITY_VIEW)).thenReturn(true);
        when(projectPermissionService.hasSystemPermission(
                3L, 9L, SystemPermissionCodes.QUALITY_RECTIFY)).thenReturn(true);

        var candidates = service.listEligibleAssignees(9L, currentUser);

        assertEquals(List.of(1L, 3L), candidates.stream().map(item -> item.getUserId()).toList());
        assertEquals("整改成员", candidates.get(0).getDisplayName());
        assertEquals("平台管理员", candidates.get(1).getDisplayName());
    }

    @Test
    void assigningUserWithoutRectifyPermissionIsRejected() {
        SysUser candidate = user(1L, 1, 0);
        when(userMapper.selectById(1L)).thenReturn(candidate);
        when(projectPermissionService.getProjectAccessStatus(1L, 9L)).thenReturn("ACTIVE");
        when(projectPermissionService.hasSystemPermission(
                1L, 9L, SystemPermissionCodes.QUALITY_VIEW)).thenReturn(true);
        when(projectPermissionService.hasSystemPermission(
                1L, 9L, SystemPermissionCodes.QUALITY_RECTIFY)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requireEligibleAssignee(1L, 9L, currentUser));

        assertTrue(exception.getMessage().contains("质量整改权限"));
    }

    @Test
    void disabledAccountCannotBeAssignedEvenWhenItWasPreviouslyAProjectMember() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, 0, 0));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requireEligibleAssignee(1L, 9L, currentUser));

        assertTrue(exception.getMessage().contains("账号未启用"));
    }

    @Test
    void openAssignmentProtectionExcludesClosedAndVoidedIssues() {
        when(issueMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        assertEquals(2L, service.countOpenAssignments(9L, 1L));

        ArgumentCaptor<LambdaQueryWrapper<QualityIssue>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(issueMapper).selectCount(wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("NOT IN"));
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs()
                .containsValue(QualityIssueService.STATUS_CLOSED));
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs()
                .containsValue(QualityIssueService.STATUS_VOIDED));
    }

    private SysUser user(Long id, Integer status, Integer deleted) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("user_" + id);
        user.setStatus(status);
        user.setDeleted(deleted);
        return user;
    }
}
