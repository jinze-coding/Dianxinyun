package com.example.siteplatform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.dto.WechatAccessApplicationVO;
import com.example.siteplatform.auth.dto.WechatApplicationReviewRequest;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.entity.WechatAccessApplication;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.mapper.WechatAccessApplicationMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.project.dto.CreateProjectUserRequest;
import com.example.siteplatform.project.dto.ProjectMemberRequest;
import com.example.siteplatform.project.dto.ProjectMemberVO;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.service.ProjectMemberService;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class WechatAccessApplicationService {

    private final WechatAccessApplicationMapper applicationMapper;
    private final SysUserMapper userMapper;
    private final ProjectInfoMapper projectMapper;
    private final ElectricBoxMapper electricBoxMapper;
    private final ProjectPermissionService permissionService;
    private final ProjectMemberService projectMemberService;
    private final WechatAuthService wechatAuthService;
    private final OperationLogMapper operationLogMapper;

    public WechatAccessApplicationService(WechatAccessApplicationMapper applicationMapper, SysUserMapper userMapper,
                                          ProjectInfoMapper projectMapper, ElectricBoxMapper electricBoxMapper,
                                          ProjectPermissionService permissionService, ProjectMemberService projectMemberService,
                                          WechatAuthService wechatAuthService, OperationLogMapper operationLogMapper) {
        this.applicationMapper = applicationMapper;
        this.userMapper = userMapper;
        this.projectMapper = projectMapper;
        this.electricBoxMapper = electricBoxMapper;
        this.permissionService = permissionService;
        this.projectMemberService = projectMemberService;
        this.wechatAuthService = wechatAuthService;
        this.operationLogMapper = operationLogMapper;
    }

    public PageResult<WechatAccessApplicationVO> list(Long projectId, String status, String keyword,
                                                       Integer pageNo, Integer pageSize, SysUser currentUser) {
        if (projectId == null && !permissionService.isPlatformAdmin(currentUser.getId())) {
            throw new BusinessException("项目ID不能为空");
        }
        if (projectId != null && !permissionService.canManageProjectMembers(currentUser.getId(), projectId)) {
            throw BusinessException.forbidden("无微信内部人员申请审批权限");
        }
        LambdaQueryWrapper<WechatAccessApplication> wrapper = new LambdaQueryWrapper<WechatAccessApplication>()
                .orderByAsc(WechatAccessApplication::getStatus)
                .orderByDesc(WechatAccessApplication::getCreateTime);
        if (projectId != null) wrapper.eq(WechatAccessApplication::getProjectId, projectId);
        if (StringUtils.hasText(status)) wrapper.eq(WechatAccessApplication::getStatus, status.trim().toUpperCase());
        List<WechatAccessApplicationVO> items = applicationMapper.selectList(wrapper).stream().map(this::toVO)
                .filter(item -> !StringUtils.hasText(keyword)
                        || (String.valueOf(item.getRealName()) + String.valueOf(item.getPhone())
                        + String.valueOf(item.getMatchedUsername())).toLowerCase(Locale.ROOT)
                        .contains(keyword.trim().toLowerCase(Locale.ROOT)))
                .toList();
        int currentPage = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null ? 20 : Math.max(1, Math.min(pageSize, 100));
        int from = Math.min((currentPage - 1) * size, items.size());
        int to = Math.min(from + size, items.size());
        return PageResult.of(currentPage, size, (long) items.size(), items.subList(from, to));
    }

    @Transactional
    public WechatAccessApplicationVO approve(Long id, WechatApplicationReviewRequest request, SysUser currentUser) {
        WechatAccessApplication application = requirePending(id);
        requireManage(application, currentUser);
        if (request == null) request = new WechatApplicationReviewRequest();
        if (request.getPermissionTemplateId() == null) throw new BusinessException("巡检权限模板不能为空");
        if (!StringUtils.hasText(request.getComment())) throw new BusinessException("审批意见不能为空");
        String accountMode = StringUtils.hasText(request.getAccountMode())
                ? request.getAccountMode().trim().toUpperCase() : (request.getUserId() != null || application.getMatchedUserId() != null ? "EXISTING" : "CREATE");
        if (!"EXISTING".equals(accountMode) && !"CREATE".equals(accountMode)) throw new BusinessException("账号处理方式只支持 EXISTING 或 CREATE");
        Long userId = "EXISTING".equals(accountMode)
                ? (request.getUserId() != null ? request.getUserId() : application.getMatchedUserId()) : null;
        if ("EXISTING".equals(accountMode) && userId == null) throw new BusinessException("请选择要绑定的已有账号");
        String projectRole = StringUtils.hasText(request.getProjectRoleCode())
                ? request.getProjectRoleCode().trim().toUpperCase() : ProjectPermissionService.ROLE_USER;
        if (userId == null) {
            CreateProjectUserRequest create = new CreateProjectUserRequest();
            create.setProjectId(application.getProjectId());
            create.setUsername(generateWechatUsername(application));
            create.setRealName(StringUtils.hasText(request.getRealName()) ? request.getRealName() : application.getRealName());
            create.setPhone(application.getPhone());
            create.setPassword(UUID.randomUUID().toString() + UUID.randomUUID());
            create.setPasswordLoginEnabled(0);
            create.setGlobalRoleCode(ProjectPermissionService.ROLE_SAFETY_ADMIN.equals(projectRole)
                    ? ProjectPermissionService.ROLE_SAFETY_ADMIN : ProjectPermissionService.ROLE_USER);
            create.setProjectRoleCode(projectRole);
            create.setPermissionTemplateId(request.getPermissionTemplateId());
            ProjectMemberVO member = projectMemberService.createUserAndJoinProject(create, currentUser);
            userId = member.getUserId();
        } else {
            SysUser user = userMapper.selectById(userId);
            if (user == null) throw BusinessException.notFound("待绑定账号不存在");
            ProjectMemberRequest member = new ProjectMemberRequest();
            member.setProjectId(application.getProjectId());
            member.setUserId(userId);
            member.setProjectRoleCode(projectRole);
            member.setPermissionTemplateId(request.getPermissionTemplateId());
            projectMemberService.saveMember(member, currentUser);
        }
        SysUser user = userMapper.selectById(userId);
        wechatAuthService.bind(user, application.getAppId(), application.getOpenid(), null, application.getPhone());
        finish(application, "APPROVED", request.getComment(), currentUser);
        application.setMatchedUserId(userId);
        applicationMapper.updateById(application);
        recordApplicationOperation(application, currentUser, "APPROVE_WECHAT_APPLICATION");
        return toVO(application);
    }

    @Transactional
    public WechatAccessApplicationVO reject(Long id, WechatApplicationReviewRequest request, SysUser currentUser) {
        WechatAccessApplication application = requirePending(id);
        requireManage(application, currentUser);
        String comment = request == null ? null : request.getComment();
        if (!StringUtils.hasText(comment)) throw new BusinessException("拒绝原因不能为空");
        finish(application, "REJECTED", comment, currentUser);
        applicationMapper.updateById(application);
        recordApplicationOperation(application, currentUser, "REJECT_WECHAT_APPLICATION");
        return toVO(application);
    }

    private void finish(WechatAccessApplication application, String status, String comment, SysUser reviewer) {
        application.setStatus(status);
        application.setReviewerId(reviewer.getId());
        application.setReviewerName(StringUtils.hasText(reviewer.getRealName()) ? reviewer.getRealName() : reviewer.getUsername());
        application.setReviewComment(StringUtils.hasText(comment) ? comment.trim() : null);
        application.setReviewTime(LocalDateTime.now());
        application.setUpdateTime(LocalDateTime.now());
    }

    private WechatAccessApplication requirePending(Long id) {
        WechatAccessApplication application = applicationMapper.selectById(id);
        if (application == null) throw BusinessException.notFound("微信权限申请不存在");
        if (!"PENDING".equals(application.getStatus())) throw new BusinessException("该申请已处理");
        return application;
    }

    private void requireManage(WechatAccessApplication application, SysUser user) {
        if (!permissionService.canManageProjectMembers(user.getId(), application.getProjectId())) {
            throw BusinessException.forbidden("无微信内部人员申请审批权限");
        }
    }

    private WechatAccessApplicationVO toVO(WechatAccessApplication application) {
        WechatAccessApplicationVO vo = new WechatAccessApplicationVO();
        BeanUtils.copyProperties(application, vo);
        ProjectInfo project = projectMapper.selectById(application.getProjectId());
        if (project != null) vo.setProjectName(project.getProjectName());
        ElectricBox box = application.getSourceId() == null ? null : electricBoxMapper.selectById(application.getSourceId());
        if (box != null) vo.setBoxCode(box.getBoxCode());
        SysUser matched = application.getMatchedUserId() == null ? null : userMapper.selectById(application.getMatchedUserId());
        if (matched != null) vo.setMatchedUsername(matched.getUsername());
        Long matchedCount = StringUtils.hasText(application.getPhone()) ? userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, application.getPhone()).eq(SysUser::getStatus, 1)) : 0L;
        vo.setApplicationType(matchedCount != null && matchedCount > 1 ? "MULTIPLE_MATCH"
                : application.getMatchedUserId() == null ? "NEW_REGISTRATION" : "PROJECT_ACCESS");
        return vo;
    }

    private String generateWechatUsername(WechatAccessApplication application) {
        String digits = StringUtils.hasText(application.getPhone()) ? application.getPhone().replaceAll("\\D", "") : "";
        String suffix = digits.length() > 4 ? digits.substring(digits.length() - 4) : digits;
        String base = "wx_" + (StringUtils.hasText(suffix) ? suffix : "user") + "_" + application.getId();
        String candidate = base;
        int index = 1;
        while (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, candidate)) > 0) {
            candidate = base + "_" + index++;
        }
        return candidate;
    }

    private void recordApplicationOperation(WechatAccessApplication application, SysUser operator, String type) {
        OperationLog log = new OperationLog();
        log.setUserId(operator.getId()); log.setUsername(operator.getUsername()); log.setOperationType(type);
        log.setOperationDesc("微信申请" + application.getId() + "处理为" + application.getStatus()
                + "，项目" + application.getProjectId() + (StringUtils.hasText(application.getReviewComment()) ? "，意见：" + application.getReviewComment() : ""));
        log.setBusinessType("WECHAT_USER"); log.setBusinessId(application.getMatchedUserId()); log.setCreateTime(LocalDateTime.now());
        operationLogMapper.insert(log);
    }
}
