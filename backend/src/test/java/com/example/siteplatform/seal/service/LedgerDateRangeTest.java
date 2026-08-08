package com.example.siteplatform.seal.service;

import com.example.siteplatform.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerDateRangeTest {

    @Test
    void dayUsesExactNaturalDay() {
        LedgerDateRange range = LedgerDateRange.resolve("DAY", LocalDate.of(2026, 8, 8), null, null);

        assertEquals(LocalDate.of(2026, 8, 8), range.startDate());
        assertEquals(LocalDate.of(2026, 8, 8), range.endDate());
    }

    @Test
    void weekUsesMondayThroughSundayBoundary() {
        LedgerDateRange range = LedgerDateRange.resolve("WEEK", LocalDate.of(2026, 8, 5), null, null);

        assertEquals(LocalDate.of(2026, 8, 3), range.startDate());
        assertEquals(LocalDate.of(2026, 8, 9), range.endDate());
    }

    @Test
    void monthUsesFirstAndLastNaturalCalendarDay() {
        LedgerDateRange range = LedgerDateRange.resolve("MONTH", LocalDate.of(2024, 2, 10), null, null);

        assertEquals(LocalDate.of(2024, 2, 1), range.startDate());
        assertEquals(LocalDate.of(2024, 2, 29), range.endDate());
    }

    @Test
    void explicitRangeAllowsAtMost366InclusiveDays() {
        LedgerDateRange accepted = LedgerDateRange.resolve(null, null,
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));
        assertEquals(LocalDate.of(2026, 1, 1), accepted.endDate());

        assertThrows(BusinessException.class, () -> LedgerDateRange.resolve(null, null,
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2)));
    }

    @Test
    void rejectsIncompleteOrReversedExplicitRange() {
        assertThrows(BusinessException.class, () -> LedgerDateRange.resolve(null, null,
                LocalDate.of(2026, 8, 1), null));
        assertThrows(BusinessException.class, () -> LedgerDateRange.resolve(null, null,
                LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1)));
    }
}
