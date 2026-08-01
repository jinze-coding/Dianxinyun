package com.example.siteplatform.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.dto.InspectionPermissionCatalogGroupVO;
import com.example.siteplatform.project.dto.InspectionPermissionCatalogItemVO;
import com.example.siteplatform.project.dto.InspectionPermissionTemplateRequest;
import com.example.siteplatform.project.dto.InspectionPermissionTemplateStatusRequest;
import com.example.siteplatform.project.dto.InspectionPermissionTemplateVO;
import com.example.siteplatform.project.entity.InspectionPermissionTemplate;
import com.example.siteplatform.project.mapper.InspectionPermissionTemplateMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class InspectionPermissionTemplateService {

    @Autowired
    private InspectionPermissionTemplateMapper templateMapper;

    @Autowired
    private ProjectPermissionService projectPermissionService;

    @Autowired
    private SysUserProjectMapper userProjectMapper;

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Autowired
    private AuthService authService;

    public List<InspectionPermissionTemplateVO> listTemplates(SysUser currentUser) {
        LambdaQueryWrapper<InspectionPermissionTemplate> wrapper = new LambdaQueryWrapper<InspectionPermissionTemplate>()
                .eq(InspectionPermissionTemplate::getDeleted, 0)
                .orderByDesc(InspectionPermissionTemplate::getBuiltin)
                .orderByAsc(InspectionPermissionTemplate::getId);
        if (!projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            wrapper.eq(InspectionPermissionTemplate::getEnabled, 1);
        }
        return templateMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    public List<InspectionPermissionCatalogGroupVO> permissionCatalog() {
        return List.of(
                new InspectionPermissionCatalogGroupVO("BOX", "电箱", List.of(
                        item(InspectionPermissionCodes.BOX_VIEW, "查看台账", "查看电箱台账、详情和二维码信息"),
                        item(InspectionPermissionCodes.BOX_MANAGE, "管理台账", "新增、编辑、停用、拆除和导入电箱"),
                        item(InspectionPermissionCodes.BOX_QR_MANAGE, "二维码/贴纸管理", "生成、补打、换绑二维码和查看二维码日志"),
                        item(InspectionPermissionCodes.BOX_PUBLIC_ACCESS, "外部访问启停", "启用或停用单个电箱外部公开只读访问")
                )),
                new InspectionPermissionCatalogGroupVO("INSPECTION", "巡检", List.of(
                        item(InspectionPermissionCodes.INSPECTION_DAILY_SUBMIT, "日检提交", "提交当前项目任意纳入巡检范围电箱的日检记录"),
                        item(InspectionPermissionCodes.INSPECTION_RECORD_VIEW, "检查记录查看", "查看项目检查记录明细")
                )),
                new InspectionPermissionCatalogGroupVO("SUMMARY", "汇总", List.of(
                        item(InspectionPermissionCodes.SUMMARY_VIEW, "巡检汇总查看", "查看项目或单箱月度巡检汇总"),
                        item(InspectionPermissionCodes.SUMMARY_EXPORT, "Excel 导出", "导出月度巡检记录 Excel")
                )),
                new InspectionPermissionCatalogGroupVO("PERMISSION", "权限", List.of(
                        item(InspectionPermissionCodes.PERMISSION_MANAGE, "项目用户授权", "加入、移除项目用户并分配权限模板")
                ))
        );
    }

    @Transactional
    public InspectionPermissionTemplateVO createTemplate(InspectionPermissionTemplateRequest request, SysUser currentUser) {
        requirePlatformAdmin(currentUser);
        validateTemplateRequest(request, false);
        String templateCode = normalizeTemplateCode(request.getTemplateCode());
        if (templateMapper.selectByTemplateCode(templateCode) != null) {
            throw new BusinessException("权限模板编码已存在");
        }
        InspectionPermissionTemplate template = new InspectionPermissionTemplate();
        template.setTemplateName(request.getTemplateName().trim());
        template.setTemplateCode(templateCode);
        template.setDescription(trimToNull(request.getDescription()));
        template.setPermissionCodes(InspectionPermissionCodes.join(request.getPermissionCodes()));
        template.setEnabled(request.getEnabled() == null ? 1 : normalizeEnabled(request.getEnabled()));
        template.setBuiltin(0);
        template.setDeleted(0);
        template.setCreateTime(LocalDateTime.now());
        template.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(templateMapper.insert(template));
        recordOperation(currentUser, template, "CREATE_PERMISSION_TEMPLATE",
                "创建巡检权限模板：" + template.getTemplateName());
        return toVO(template);
    }

    @Transactional
    public InspectionPermissionTemplateVO updateTemplate(Long id, InspectionPermissionTemplateRequest request, SysUser currentUser) {
        requirePlatformAdmin(currentUser);
        InspectionPermissionTemplate template = requireTemplate(id);
        validateTemplateRequest(request, true);
        Integer nextEnabled = request.getEnabled() == null
                ? template.getEnabled()
                : normalizeEnabled(request.getEnabled());
        requireBuiltinEnabledUnchanged(template, nextEnabled);
        Set<Long> affectedUserIds = affectedUserIds(id);
        template.setTemplateName(request.getTemplateName().trim());
        template.setDescription(trimToNull(request.getDescription()));
        template.setPermissionCodes(InspectionPermissionCodes.join(request.getPermissionCodes()));
        if (request.getEnabled() != null) {
            template.setEnabled(nextEnabled);
        }
        if (template.getBuiltin() == null || template.getBuiltin() == 0) {
            String nextCode = StringUtils.hasText(request.getTemplateCode())
                    ? normalizeTemplateCode(request.getTemplateCode())
                    : template.getTemplateCode();
            InspectionPermissionTemplate existing = templateMapper.selectByTemplateCode(nextCode);
            if (existing != null && !existing.getId().equals(template.getId())) {
                throw new BusinessException("权限模板编码已存在");
            }
            template.setTemplateCode(nextCode);
        }
        template.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(templateMapper.updateById(template));
        recordOperation(currentUser, template, "UPDATE_PERMISSION_TEMPLATE",
                "修改巡检权限模板：" + template.getTemplateName()
                        + "，受影响用户数：" + affectedUserIds.size());
        invalidateAffectedUsers(affectedUserIds);
        return toVO(template);
    }

    @Transactional
    public InspectionPermissionTemplateVO updateStatus(Long id, InspectionPermissionTemplateStatusRequest request, SysUser currentUser) {
        requirePlatformAdmin(currentUser);
        if (request == null || request.getEnabled() == null) {
            throw new BusinessException("启停状态不能为空");
        }
        InspectionPermissionTemplate template = requireTemplate(id);
        Integer nextEnabled = Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0;
        requireBuiltinEnabledUnchanged(template, nextEnabled);
        if (Objects.equals(template.getEnabled(), nextEnabled)) {
            return toVO(template);
        }
        Set<Long> affectedUserIds = affectedUserIds(id);
        template.setEnabled(nextEnabled);
        template.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(templateMapper.updateById(template));
        recordOperation(currentUser, template, "CHANGE_PERMISSION_TEMPLATE_STATUS",
                (Integer.valueOf(1).equals(template.getEnabled()) ? "启用" : "停用")
                        + "巡检权限模板：" + template.getTemplateName()
                        + "，受影响用户数：" + affectedUserIds.size());
        invalidateAffectedUsers(affectedUserIds);
        return toVO(template);
    }

    public InspectionPermissionTemplate requireEnabledTemplate(Long templateId) {
        if (templateId == null) {
            throw new BusinessException("权限模板不能为空");
        }
        InspectionPermissionTemplate template = requireTemplate(templateId);
        if (template.getEnabled() == null || template.getEnabled() != 1) {
            throw new BusinessException("权限模板已停用");
        }
        return template;
    }

    public Long defaultTemplateIdForRole(String projectRoleCode) {
        String templateCode = switch (projectPermissionService.normalizeProjectRoleCode(projectRoleCode)) {
            case ProjectPermissionService.ROLE_PROJECT_ADMIN -> "PROJECT_ADMIN";
            case ProjectPermissionService.ROLE_SAFETY_ADMIN -> "SAFETY_ADMIN";
            default -> "USER";
        };
        InspectionPermissionTemplate template = templateMapper.selectByTemplateCode(templateCode);
        return template == null ? null : template.getId();
    }

    private InspectionPermissionTemplate requireTemplate(Long id) {
        if (id == null) {
            throw new BusinessException("权限模板ID不能为空");
        }
        InspectionPermissionTemplate template = templateMapper.selectById(id);
        if (template == null || (template.getDeleted() != null && template.getDeleted() == 1)) {
            throw BusinessException.notFound("权限模板不存在");
        }
        return template;
    }

    private Set<Long> affectedUserIds(Long templateId) {
        List<Long> userIds = userProjectMapper.selectActiveUserIdsByInspectionPermissionTemplateId(templateId);
        return userIds == null ? Set.of() : new LinkedHashSet<>(userIds);
    }

    private void requireBuiltinEnabledUnchanged(InspectionPermissionTemplate template, Integer nextEnabled) {
        if (Integer.valueOf(1).equals(template.getBuiltin())
                && !Objects.equals(template.getEnabled(), nextEnabled)) {
            throw new BusinessException("内置权限模板不可启停");
        }
    }

    private void invalidateAffectedUsers(Set<Long> userIds) {
        userIds.stream().filter(Objects::nonNull).forEach(userId -> {
            projectPermissionService.clearUserProjectsCache(userId);
            authService.logout(userId);
            authService.repeatLogoutAfterCommit(userId);
        });
    }

    private void recordOperation(SysUser operator, InspectionPermissionTemplate template,
                                 String operationType, String description) {
        OperationLog log = new OperationLog();
        log.setUserId(operator.getId());
        log.setUsername(operator.getUsername());
        log.setOperationType(operationType);
        log.setOperationDesc(description);
        log.setBusinessType("INSPECTION_PERMISSION_TEMPLATE");
        log.setBusinessId(template.getId());
        log.setCreateTime(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    private void validateTemplateRequest(InspectionPermissionTemplateRequest request, boolean update) {
        if (request == null) {
            throw new BusinessException("权限模板信息不能为空");
        }
        if (!StringUtils.hasText(request.getTemplateName())) {
            throw new BusinessException("权限模板名称不能为空");
        }
        if (!update && !StringUtils.hasText(request.getTemplateCode())) {
            throw new BusinessException("权限模板编码不能为空");
        }
        try {
            InspectionPermissionCodes.normalize(request.getPermissionCodes());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
    }

    private InspectionPermissionCatalogItemVO item(String code, String name, String description) {
        return new InspectionPermissionCatalogItemVO(code, name, description);
    }

    private InspectionPermissionTemplateVO toVO(InspectionPermissionTemplate template) {
        InspectionPermissionTemplateVO vo = new InspectionPermissionTemplateVO();
        BeanUtils.copyProperties(template, vo);
        vo.setPermissionCodes(InspectionPermissionCodes.parse(template.getPermissionCodes()));
        return vo;
    }

    private void requirePlatformAdmin(SysUser currentUser) {
        if (!projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            throw BusinessException.forbidden("只有平台管理员可以维护权限模板");
        }
    }

    private String normalizeTemplateCode(String code) {
        String value = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("^[A-Z0-9_\\-]{2,60}$")) {
            throw new BusinessException("权限模板编码需为2-60位大写字母、数字、下划线或横线");
        }
        return value;
    }

    private Integer normalizeEnabled(Integer enabled) {
        return enabled != null && enabled == 1 ? 1 : 0;
    }

    private void requireSingleWrite(int affectedRows) {
        if (affectedRows != 1) {
            throw BusinessException.of(409, "巡检权限模板状态已变化，请刷新后重试");
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
