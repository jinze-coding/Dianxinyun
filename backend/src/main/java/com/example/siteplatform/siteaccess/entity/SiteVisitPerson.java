package com.example.siteplatform.siteaccess.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("site_visit_person")
public class SiteVisitPerson {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long invitationId;
    private Long projectId;
    private String personType;
    private String personName;
    private String idCardEncrypted;
    private String idCardHash;
    private Integer sortOrder;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
