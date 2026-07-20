package com.example.siteplatform.electricbox.dto;

import lombok.Data;

@Data
public class ElectricBoxQrRebindRequest {
    private String qrCode;
    private String reason;
}
