package com.example.siteplatform.electricbox.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ElectricBoxImportResultVO {
    private Integer totalRows = 0;
    private Integer successRows = 0;
    private Integer warningRows = 0;
    private Integer errorRows = 0;
    private List<ElectricBoxImportRowVO> rows = new ArrayList<>();
}
