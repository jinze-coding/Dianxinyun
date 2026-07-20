package com.example.siteplatform.auth.dto;

import lombok.Data;

import java.util.List;

@Data
public class WechatUserPageVO {
    private List<WechatUserListItemVO> records;
    private Long total;
    private Integer page;
    private Integer size;

    public static WechatUserPageVO of(Integer page, Integer size, Long total, List<WechatUserListItemVO> records) {
        WechatUserPageVO result = new WechatUserPageVO();
        result.setPage(page);
        result.setSize(size);
        result.setTotal(total);
        result.setRecords(records);
        return result;
    }
}
