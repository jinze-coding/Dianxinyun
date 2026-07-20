package com.example.siteplatform.electricbox.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("electric_box_qr_log")
public class ElectricBoxQrLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long electricBoxId;
    private String boxCode;
    private String actionType;
    private String qrType;
    private String oldQrCode;
    private String newQrCode;
    private Long operatorUserId;
    private String operatorUsername;
    private String reason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
