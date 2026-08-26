package com.example.siteplatform.inspection.general.controller;

import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.inspection.general.service.GeneralInspectionReportingService;
import com.example.siteplatform.inspection.general.vo.PublicGeneralInspectionMonthlyVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/v1/public/general-inspection-points")
@RequiredArgsConstructor
public class PublicGeneralInspectionController {

    private final RedisRateLimitService rateLimitService;
    private final GeneralInspectionReportingService reportingService;

    @GetMapping("/{code}/monthly-records")
    public Result<PublicGeneralInspectionMonthlyVO> monthly(@PathVariable String code,
                                                            @RequestParam(required = false) String month,
                                                            HttpServletRequest request) {
        // 必须先限流，再执行任何点位或巡检数据库查询。
        rateLimitService.check("public-general-inspection-monthly", request.getRemoteAddr(),
                60, Duration.ofMinutes(10));
        try {
            return Result.success(reportingService.publicMonthly(code,
                    month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month.trim())));
        } catch (DateTimeParseException ex) {
            throw new BusinessException("月份格式必须为yyyy-MM");
        }
    }
}
