package com.example.siteplatform.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_role")
public class SystemRole {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleName;
    private String roleCode;
    private String description;
    private String scopeType;
    /** 仅项目经理角色可管理项目成员，且其授予/撤销只能由系统管理员执行。 */
    private Integer projectManagerRole;
    private Integer builtin;
    private Integer enabled;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableField(exist = false)
    private List<Long> menuIds;
    @TableField(exist = false)
    private List<Long> permissionIds;
    /** 用于角色选择器的权限摘要，不暴露权限主键。 */
    @TableField(exist = false)
    private List<String> permissionNames;
    /** 资料、巡检、质量的跨端统一开关，不暴露原始 Web / 小程序菜单拆分。 */
    @TableField(exist = false)
    private List<String> businessModuleCodes;

    public String getCode() { return roleCode; }
    public String getName() { return roleName; }
}
