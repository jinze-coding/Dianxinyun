package com.example.siteplatform.inspection.controller;

import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.inspection.service.InspectionService;
import com.example.siteplatform.inspection.vo.PublicElectricBoxSummaryVO;
import com.example.siteplatform.inspection.vo.PublicElectricBoxMonthlyVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Tag(name = "公开电箱扫码", description = "外部扫码只读脱敏汇总接口")
@RestController
@RequestMapping("/api/v1/public/electric-boxes")
public class PublicElectricBoxController {

    @Autowired
    private InspectionService inspectionService;

    @Autowired
    private RedisRateLimitService rateLimitService;

    @Operation(summary = "获取电箱公开巡检汇总")
    @GetMapping("/{publicCode}/summary")
    public Result<PublicElectricBoxSummaryVO> getSummary(@PathVariable String publicCode,
                                                         HttpServletRequest request) {
        rateLimitService.check("public-electric-box-summary", request.getRemoteAddr(),
                60, Duration.ofMinutes(10));
        return Result.success(inspectionService.getPublicSummary(publicCode));
    }

    @Operation(summary = "获取电箱公开月度检查表")
    @GetMapping("/{publicCode}/monthly-records")
    public Result<PublicElectricBoxMonthlyVO> getMonthlyRecords(
            @PathVariable String publicCode,
            @RequestParam(required = false) String month,
            HttpServletRequest request) {
        rateLimitService.check("public-electric-box-monthly", request.getRemoteAddr(),
                30, Duration.ofMinutes(10));
        return Result.success(inspectionService.getPublicMonthly(publicCode, month));
    }
}
