package com.example.siteplatform.seal.service;

import com.example.siteplatform.common.BusinessException;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.util.Locale;

public record LedgerDateRange(LocalDate startDate, LocalDate endDate) {
    public static LedgerDateRange resolve(String period, LocalDate anchorDate,
                                          LocalDate explicitStart, LocalDate explicitEnd) {
        if (explicitStart != null || explicitEnd != null) {
            if (explicitStart == null || explicitEnd == null) throw new BusinessException("开始和结束日期必须同时填写");
            validate(explicitStart, explicitEnd);
            return new LedgerDateRange(explicitStart, explicitEnd);
        }
        LocalDate anchor = anchorDate == null ? LocalDate.now(ZoneId.of("Asia/Shanghai")) : anchorDate;
        String unit = StringUtils.hasText(period) ? period.trim().toUpperCase(Locale.ROOT) : "MONTH";
        LedgerDateRange range = switch (unit) {
            case "DAY" -> new LedgerDateRange(anchor, anchor);
            case "WEEK" -> {
                LocalDate monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new LedgerDateRange(monday, monday.plusDays(6));
            }
            case "MONTH" -> new LedgerDateRange(anchor.withDayOfMonth(1), anchor.withDayOfMonth(anchor.lengthOfMonth()));
            default -> throw new BusinessException("导出周期必须为 DAY、WEEK 或 MONTH");
        };
        validate(range.startDate, range.endDate);
        return range;
    }

    private static void validate(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) throw new BusinessException("开始日期不能晚于结束日期");
        if (ChronoUnit.DAYS.between(start, end) > 365) throw new BusinessException("单次台账导出不能超过366天");
    }
}
