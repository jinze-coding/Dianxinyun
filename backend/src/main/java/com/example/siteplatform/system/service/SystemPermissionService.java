package com.example.siteplatform.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.dto.MenuVO;
import com.example.siteplatform.system.entity.SystemMenu;
import com.example.siteplatform.system.entity.SystemPermission;
import com.example.siteplatform.system.mapper.SystemMenuMapper;
import com.example.siteplatform.system.mapper.SystemPermissionMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SystemPermissionService {

    private final SystemMenuMapper menuMapper;
    private final SystemPermissionMapper permissionMapper;
    private final SysUserMapper userMapper;

    public SystemPermissionService(SystemMenuMapper menuMapper, SystemPermissionMapper permissionMapper,
                                   SysUserMapper userMapper) {
        this.menuMapper = menuMapper;
        this.permissionMapper = permissionMapper;
        this.userMapper = userMapper;
    }

    public boolean isPlatformAdmin(Long userId) {
        if (userId == null) return false;
        List<String> roles = userMapper.selectRoleCodesByUserId(userId);
        return roles != null && roles.contains(ProjectPermissionService.ROLE_PLATFORM_ADMIN);
    }

    public List<String> permissionCodes(Long userId) {
        if (isPlatformAdmin(userId)) {
            return permissionMapper.selectList(new LambdaQueryWrapper<SystemPermission>()
                            .eq(SystemPermission::getEnabled, 1)
                            .eq(SystemPermission::getDeleted, 0)
                            .orderByAsc(SystemPermission::getPermissionCode))
                    .stream().map(SystemPermission::getPermissionCode).distinct().toList();
        }
        List<String> codes = permissionMapper.selectPlatformCodesByUserId(userId);
        return codes == null ? List.of() : codes;
    }

    public boolean hasPermission(Long userId, String permissionCode) {
        if (isPlatformAdmin(userId)) return true;
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
        List<String> codes = permissionMapper.selectCodesByUserIdAndProject(userId, projectId);
        return codes != null && codes.contains(permissionCode);
    }

    public List<String> projectRolePermissionCodes(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) return List.of();
        List<String> codes = permissionMapper.selectCodesByProjectRole(
                roleCode.trim().toUpperCase(Locale.ROOT));
        return codes == null ? List.of() : codes;
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
        List<SystemMenu> menus = isPlatformAdmin(userId)
                ? menuMapper.selectList(new LambdaQueryWrapper<SystemMenu>()
                        .eq(SystemMenu::getEnabled, 1).eq(SystemMenu::getVisible, 1)
                        .eq(SystemMenu::getDeleted, 0)
                        .orderByAsc(SystemMenu::getSortOrder).orderByAsc(SystemMenu::getId))
                : menuMapper.selectEnabledByUserId(userId);
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
}
