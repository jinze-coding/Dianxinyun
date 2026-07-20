package com.example.siteplatform.electricbox.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("electric_box")
public class ElectricBox {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String boxCode;
    private String boxName;
    private String installLocation;
    private Long responsibleElectricianId;
    private String responsibleElectricianName;
    private Long safetyManagerId;
    private String safetyManagerName;
    private String qrCode;
    private String qrStatus;
    private String status;
    private String publicCode;
    private Integer publicAccessEnabled;
    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
