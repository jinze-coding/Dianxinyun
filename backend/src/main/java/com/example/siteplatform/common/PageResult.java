package com.example.siteplatform.common;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    private Integer pageNo;
    private Integer pageSize;
    private Long total;
    private List<T> records;

    public static <T> PageResult<T> of(Integer pageNo, Integer pageSize, Long total, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        result.setTotal(total);
        result.setRecords(records);
        return result;
    }

    public List<T> getItems() {
        return records;
    }
}
