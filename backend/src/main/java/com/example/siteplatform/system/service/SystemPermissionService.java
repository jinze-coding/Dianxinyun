package com.example.siteplatform.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.BusinessModuleCodes;
import com.example.siteplatform.system.dto.MenuVO;
import com.example.siteplatform.system.entity.SystemMenu;
import com.example.siteplatform.system.entity.SystemPermission;
import com.example.siteplatform.system.mapper.SystemMenuMapper;
import com.example.siteplatform.system.mapper.SystemPermissionMapper;
import com.example.siteplatform.system.mapper.SystemRoleBusinessModuleMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SystemPermissionService {

    private final SystemMenuMapper menuMapper;
    private final SystemPermissionMapper permissionMapper;
    private final SysUserMapper userMapper;
    private final SystemRoleBusinessModuleMapper roleBusinessModuleMapper;

    public SystemPermissionService(SystemMenuMapper menuMapper, SystemPermissionMapper permissionMapper,
                                   SysUserMapper userMapper,
                                   SystemRoleBusinessModuleMapper roleBusinessModuleMapper) {
        this.menuMapper = menuMapper;
        this.permissionMapper = permissionMapper;
        this.userMapper = userMapper;
        this.roleBusinessModuleMapper = roleBusinessModuleMapper;
    }

    public boolean isPlatformAdmin(Long userId) {
        if (userId == null) return false;
        List<String> roles = userMapper.selectRoleCodesByUserId(userId);
        return roles != null && roles.contains(ProjectPermissionService.ROLE_PLATFORM_ADMIN);
    }

    public List<String> permissionCodes(Long userId) {
        if (isPlatformAdmin(userId)) {
            return allEnabledPermissionCodes();
        }
        Set<String> effectiveModules = businessModuleCodes(userId);
        List<String> codes = permissionMapper.selectPlatformCodesByUserId(userId);
        return filterBusinessPermissionCodes(codes, effectiveModules);
    }

    public boolean hasPermission(Long userId, String permissionCode) {
        if (isPlatformAdmin(userId)) return true;
        String businessModule = BusinessModuleCodes.fromPermissionCode(permissionCode);
        if (businessModule != null && !businessModuleCodes(userId).contains(businessModule)) {
            return false;
        }
        List<String> codes = permissionMapper.selectCodesByUserId(userId);
        return codes != null && codes.contains(permissionCode);
    }

    /**
     * 平台管理权限只能由平台角色提供。项目角色权限会按项目范围参与业务接口鉴权，
     * 但不能借由聚合权限进入注册、用户、角色、菜单、微信绑定或审计等全局管理接口。
     */
    public boolean hasPlatformPermission(Long userId, String permissionCode) {
        if (isPlatformAdmin(userId)) return true;
        List<String> codes = permissionMapper.selectPlatformCodesByUserId(userId);
        return codes != null && codes.contains(permissionCode);
    }

    public boolean hasProjectPermission(Long userId, Long projectId, String permissionCode) {
        if (userId == null || projectId == null || permissionCode == null) return false;
        if (isPlatformAdmin(userId)) return true;
        String businessModule = BusinessModuleCodes.fromPermissionCode(permissionCode);
        if (businessModule != null && !businessModuleCodes(userId, projectId).contains(businessModule)) {
            return false;
        }
        List<String> codes = permissionMapper.selectCodesByUserIdAndProject(userId, projectId);
        return codes != null && codes.contains(permissionCode);
    }

    public List<String> projectRolePermissionCodes(String roleCode) {
        return List.of();
    }

    /** 当前用户在指定有效项目内由全部项目角色聚合出的权限。 */
    public List<String> projectPermissionCodes(Long userId, Long projectId) {
        if (userId == null || projectId == null) return List.of();
        if (isPlatformAdmin(userId)) {
            return allEnabledPermissionCodes();
        }
        Set<String> effectiveModules = businessModuleCodes(userId, projectId);
        List<String> codes = permissionMapper.selectProjectCodesByUserIdAndProject(userId, projectId);
        return filterBusinessPermissionCodes(codes, effectiveModules);
    }

    /** 当前用户在指定有效项目中由项目角色获得的可见菜单编码。 */
    public List<String> projectMenuCodes(Long userId, Long projectId) {
        if (userId == null || projectId == null) return List.of();
        if (isPlatformAdmin(userId)) {
            return menuMapper.selectList(new LambdaQueryWrapper<SystemMenu>()
                            .eq(SystemMenu::getEnabled, 1)
                            .eq(SystemMenu::getVisible, 1)
                            .eq(SystemMenu::getDeleted, 0)
                            .orderByAsc(SystemMenu::getSortOrder)
                            .orderByAsc(SystemMenu::getId))
                    .stream().map(SystemMenu::getMenuCode).filter(java.util.Objects::nonNull).distinct().toList();
        }
        List<String> codes = menuMapper.selectEnabledCodesByUserIdAndProject(userId, projectId);
        return filterBusinessMenuCodes(codes, businessModuleCodes(userId, projectId));
    }

    public void requirePermission(SysUser user, String permissionCode) {
        if (user == null || !hasPermission(user.getId(), permissionCode)) {
            throw BusinessException.forbidden("无操作权限：" + permissionCode);
        }
    }

    public void requirePlatformPermission(SysUser user, String permissionCode) {
        if (user == null || !hasPlatformPermission(user.getId(), permissionCode)) {
            throw BusinessException.forbidden("无平台操作权限：" + permissionCode);
        }
    }

    public void requireProjectPermission(SysUser user, Long projectId, String permissionCode) {
        if (user == null || !hasProjectPermission(user.getId(), projectId, permissionCode)) {
            throw BusinessException.forbidden("无当前项目操作权限：" + permissionCode);
        }
    }

    public void requireAnyPermission(SysUser user, String... permissionCodes) {
        if (user == null || permissionCodes == null
                || java.util.Arrays.stream(permissionCodes)
                .filter(code -> code != null && !code.isBlank())
                .noneMatch(code -> hasPermission(user.getId(), code))) {
            throw BusinessException.forbidden("无操作权限");
        }
    }

    public void requireAnyPlatformPermission(SysUser user, String... permissionCodes) {
        if (user == null || permissionCodes == null
                || java.util.Arrays.stream(permissionCodes)
                .filter(code -> code != null && !code.isBlank())
                .noneMatch(code -> hasPlatformPermission(user.getId(), code))) {
            throw BusinessException.forbidden("无平台操作权限");
        }
    }

    public void requirePlatformAdmin(SysUser user) {
        if (user == null || !isPlatformAdmin(user.getId())) {
            throw BusinessException.forbidden("仅平台管理员可执行该操作");
        }
    }

    public List<MenuVO> menuTree(Long userId) {
        Set<String> effectiveModules = businessModuleCodes(userId);
        List<SystemMenu> source = isPlatformAdmin(userId)
                ? menuMapper.selectList(new LambdaQueryWrapper<SystemMenu>()
                .eq(SystemMenu::getEnabled, 1)
                .eq(SystemMenu::getVisible, 1)
                .eq(SystemMenu::getDeleted, 0)
                .orderByAsc(SystemMenu::getSortOrder)
                .orderByAsc(SystemMenu::getId))
                : menuMapper.selectEnabledByUserId(userId);
        List<SystemMenu> menus = source.stream()
                .filter(menu -> {
                    String businessModule = BusinessModuleCodes.fromMenuCode(menu.getMenuCode());
                    return businessModule == null || effectiveModules.contains(businessModule);
                })
                .toList();
        Map<Long, MenuVO> byId = new LinkedHashMap<>();
        menus.forEach(menu -> byId.put(menu.getId(), MenuVO.from(menu)));
        List<MenuVO> roots = new ArrayList<>();
        for (MenuVO item : byId.values()) {
            MenuVO parent = item.getParentId() == null ? null : byId.get(item.getParentId());
            if (parent == null) roots.add(item);
            else parent.getChildren().add(item);
        }
        return roots;
    }

    /** 当前用户任一有效作用域中的模块，用于没有 projectId 的旧接口拦截。 */
    public Set<String> businessModuleCodes(Long userId) {
        if (userId == null) return Set.of();
        if (isPlatformAdmin(userId)) return Set.copyOf(BusinessModuleCodes.ALL);
        List<String> codes = roleBusinessModuleMapper.selectModuleCodesByUserId(userId);
        return normalizeBusinessModuleCodes(codes);
    }

    /** 当前项目中有效的平台/项目角色共同授予的模块。 */
    public Set<String> businessModuleCodes(Long userId, Long projectId) {
        if (userId == null || projectId == null) return Set.of();
        if (isPlatformAdmin(userId)) return Set.copyOf(BusinessModuleCodes.ALL);
        List<String> codes = roleBusinessModuleMapper.selectModuleCodesByUserIdAndProject(userId, projectId);
        return normalizeBusinessModuleCodes(codes);
    }

    public boolean hasBusinessModule(Long userId, Long projectId, String moduleCode) {
        return businessModuleCodes(userId, projectId).contains(moduleCode);
    }

    private Set<String> normalizeBusinessModuleCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (String code : codes) {
            if (BusinessModuleCodes.isBusinessModule(code)) {
                result.add(code.trim().toUpperCase(java.util.Locale.ROOT));
            }
        }
        return Set.copyOf(result);
    }

    private List<String> allEnabledPermissionCodes() {
        return permissionMapper.selectList(new LambdaQueryWrapper<SystemPermission>()
                        .eq(SystemPermission::getEnabled, 1)
                        .eq(SystemPermission::getDeleted, 0)
                        .orderByAsc(SystemPermission::getPermissionCode))
                .stream().map(SystemPermission::getPermissionCode)
                .filter(java.util.Objects::nonNull).distinct().toList();
    }

    private List<String> filterBusinessPermissionCodes(List<String> codes, Set<String> effectiveModules) {
        if (codes == null || codes.isEmpty()) return List.of();
        return codes.stream().filter(code -> {
            String businessModule = BusinessModuleCodes.fromPermissionCode(code);
            return businessModule == null || effectiveModules.contains(businessModule);
        }).distinct().toList();
    }

    private List<String> filterBusinessMenuCodes(List<String> codes, Set<String> effectiveModules) {
        if (codes == null || codes.isEmpty()) return List.of();
        return codes.stream().filter(code -> {
            String businessModule = BusinessModuleCodes.fromMenuCode(code);
            return businessModule == null || effectiveModules.contains(businessModule);
        }).distinct().toList();
    }
}
