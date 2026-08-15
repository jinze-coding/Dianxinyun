package com.example.siteplatform.siteaccess.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("site_guard_visit_qr")
public class SiteGuardVisitQr {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String sceneTokenHash;
    private String sceneTokenEncrypted;
    private String qrStatus;
    private Integer qrVersion;
    @TableField(exist = false)
    private Long currentProjectId;
    private Long createdById;
    private String createdByName;
    private Long updatedById;
    private String updatedByName;
    private Integer version;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
