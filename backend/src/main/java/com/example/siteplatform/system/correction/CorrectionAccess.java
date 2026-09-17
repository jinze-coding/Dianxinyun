package com.example.siteplatform.system.correction;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.project.service.ProjectBusinessModuleService;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.service.SystemPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.example.siteplatform.system.correction.CorrectionRepository.*;

@Service
@RequiredArgsConstructor
public class CorrectionAccess {
    private final CorrectionRepository repo;
    private final SystemPermissionService permissions;
    private final ProjectPermissionService projectPermissions;
    private final ProjectBusinessModuleService modules;
    private final SysUserMapper users;
    public void operator(SysUser user) {
        if(user==null) throw BusinessException.forbidden("仅平台管理员可执行数据纠错");
        SysUser live=users.selectById(user.getId());
        if(live==null || !Integer.valueOf(1).equals(live.getStatus())) throw BusinessException.forbidden("管理员账号已失效");
        permissions.requirePlatformAdmin(live);
        permissions.requirePlatformPermission(live,"system.data.correct");
    }
    public void project(long projectId,CorrectionCatalog.Type type,SysUser user) {
        operator(user);
        var project=repo.one("SELECT id,project_status FROM project_info WHERE id=? AND deleted=0",projectId);
        if("stopped".equalsIgnoreCase(string(project.get("project_status")))) throw BusinessException.forbidden("项目已停用");
        if(type.module()!=null) modules.requireEnabled(projectId,type.module());
    }
    public long projectId(CorrectionCatalog.Type type,Map<String,Object> row) {
        return id(row.get(type.table().equals("project_info")?"id":"project_id"));
    }
    public Map<String,Object> target(CorrectionCatalog.Type type,long id,SysUser user,boolean lock) {
        var row=repo.one("SELECT * FROM "+type.table()+" WHERE id=? AND ("+type.predicate()+")",id);
        long projectId=projectId(type,row); project(projectId,type,user);
        if(lock) {
            repo.one("SELECT id FROM project_info WHERE id=? AND deleted=0 FOR UPDATE",projectId);
            repo.one("SELECT id FROM sys_user WHERE id=? AND deleted=0 FOR UPDATE",user.getId());
            if(type.code().equals("ELECTRIC_INSPECTION"))repo.one("SELECT id FROM electric_box WHERE id=? AND deleted=0 FOR UPDATE",row.get("electric_box_id"));
            if(type.code().equals("EDGE_RECTIFICATION"))repo.one("SELECT id FROM general_inspection_task WHERE id=? FOR UPDATE",row.get("task_id"));
            row=repo.one("SELECT * FROM "+type.table()+" WHERE id=? AND ("+type.predicate()+") FOR UPDATE",id);
            if(projectId(type,row)!=projectId) throw BusinessException.of(409,"记录所属项目已变化");
            project(projectId,type,user);
        }
        return row;
    }
    public Map<String,Object> person(long projectId,long userId,String permission,boolean lock) {
        var row=repo.one("SELECT id,username,real_name,phone,status FROM sys_user WHERE id=? AND deleted=0"+(lock?" FOR UPDATE":""),userId);
        boolean member=repo.count("SELECT COUNT(*) FROM sys_user_project WHERE project_id=? AND user_id=? AND status='ACTIVE'",projectId,userId)>0;
        if(!"1".equals(string(row.get("status"))) || (!member && !permissions.isPlatformAdmin(userId))) throw new BusinessException("所选人员不是项目有效成员");
        if(permission!=null && !eligible(userId,projectId,permission)) throw new BusinessException("所选人员没有对应业务资格");
        if("quality.rectify".equals(permission) && !projectPermissions.hasSystemPermission(userId,projectId,"quality.view")) throw new BusinessException("整改人缺少质量查看权限");
        return row;
    }
    private boolean eligible(long userId,long projectId,String permission) {
        if(permission.startsWith("inspection.") && !projectPermissions.hasSystemPermission(userId,projectId,"inspection.view"))return false;
        if(permission.equals("inspection.submit") && !projectPermissions.hasInspectionPermission(userId,projectId,"INSPECTION_DAILY_SUBMIT"))return false;
        if(permission.equals("inspection.review") && !projectPermissions.hasInspectionPermission(userId,projectId,"INSPECTION_REVIEW"))return false;
        if(permission.equals("safety_committee.submit") && !projectPermissions.hasSystemPermission(userId,projectId,"safety_committee.view"))return false;
        return permission.startsWith("EDGE_")?projectPermissions.hasInspectionPermission(userId,projectId,permission):projectPermissions.hasSystemPermission(userId,projectId,permission);
    }
    public List<Map<String,Object>> candidates(long projectId,CorrectionCatalog.Type type,CorrectionCatalog.Field field,SysUser user) {
        project(projectId,type,user);
        var rows=repo.rows("SELECT DISTINCT u.id,u.username,u.real_name FROM sys_user u WHERE u.deleted=0 AND u.status=1 AND "
                +"(EXISTS(SELECT 1 FROM sys_user_project p WHERE p.user_id=u.id AND p.project_id=? AND p.status='ACTIVE') OR "
                +"EXISTS(SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.role_code='PLATFORM_ADMIN' AND r.deleted=0)) ORDER BY u.real_name,u.id",projectId);
        return rows.stream().filter(row->{try{person(projectId,id(row.get("id")),field.permission(),false);return true;}catch(BusinessException e){return false;}}).toList();
    }
}
