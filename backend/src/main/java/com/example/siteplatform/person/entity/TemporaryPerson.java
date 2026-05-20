package com.example.siteplatform.person.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("temporary_person")
public class TemporaryPerson {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String name;
    private String gender;
    private String idcard;
    private String phone;
    private String unit;
    private String role;
    private LocalDateTime entryTime;
    private String status;
    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
