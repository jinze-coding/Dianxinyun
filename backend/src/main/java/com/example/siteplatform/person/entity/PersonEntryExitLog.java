package com.example.siteplatform.person.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("person_entry_exit_log")
public class PersonEntryExitLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long personId;
    private String actionType;
    private LocalDateTime occurredAt;
    private Long operatorId;
    private String operatorName;
    private String remark;
    private LocalDateTime createTime;
}
