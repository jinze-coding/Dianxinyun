package com.example.siteplatform.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_menu")
public class SystemMenu {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String clientType;
    private String menuCode;
    private String menuName;
    private String resourceType;
    private String routePath;
    private String permissionCode;
    private Integer sortOrder;
    private Integer visible;
    private Integer enabled;
    private Integer builtin;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableField(exist = false)
    private Object status;

    public String getCode() { return menuCode; }
    public String getName() { return menuName; }
    public String getPath() { return routePath; }
    public String getType() { return resourceType; }
}
