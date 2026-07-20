package com.example.siteplatform.electricbox.dto;

import lombok.Data;

import java.util.List;

@Data
public class ElectricBoxQrPrintLogRequest {
    private List<String> qrTypes;
    private String reason;
}
