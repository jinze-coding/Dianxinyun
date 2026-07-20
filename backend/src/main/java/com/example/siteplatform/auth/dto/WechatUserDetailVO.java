package com.example.siteplatform.auth.dto;

import lombok.Data;
import java.util.List;

@Data
public class WechatUserDetailVO {
    private Long userId;
    private String username;
    private String realName;
    private String phone;
    private Integer status;
    private List<WechatBindingVO> bindings;
    private List<WechatUserProjectVO> projects;
    private List<WechatAccessApplicationVO> applications;
    private List<WechatOperationLogVO> operationLogs;
}
