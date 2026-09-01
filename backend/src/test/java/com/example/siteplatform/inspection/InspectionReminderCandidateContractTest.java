package com.example.siteplatform.inspection;

import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.inspection.general.mapper.GeneralInspectionTaskMapper;
import com.example.siteplatform.quality.mapper.QualityWeeklyReminderSettingMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionReminderCandidateContractTest {

    @Test
    void candidateQueriesStayUnboundedAndExcludeAlreadyNotifiedRows() throws Exception {
        String qualitySql = selectSql(QualityWeeklyReminderSettingMapper.class.getMethod(
                "selectEnabledSettings", LocalDate.class));
        String electricSql = selectSql(ElectricBoxMapper.class.getMethod(
                "selectPendingSubmissionReminderBoxes", Long.class, LocalDate.class));
        String edgeSql = selectSql(GeneralInspectionTaskMapper.class.getMethod(
                "selectPendingReminderCandidateIds", Long.class, LocalDateTime.class, LocalDateTime.class));

        assertThat(qualitySql).contains(
                "NOT EXISTS",
                "notification.dedup_key = CONCAT(",
                "'reminder:qweek:'");
        assertThat(electricSql).contains(
                "NOT EXISTS",
                "notification.dedup_key = CONCAT('reminder:ebox:'",
                "FROM inspection_record record",
                "record.status <> 'DRAFT'",
                "FROM electric_box_inspection_scope scope",
                "scope.effective_date <= #{date}");
        assertThat(qualitySql).doesNotContain("LIMIT");
        assertThat(edgeSql).doesNotContain("LIMIT");
        assertThat(electricSql).doesNotContain("ORDER BY box.id LIMIT");
    }

    @Test
    void edgeCandidatesRequireAnAssigneeAndQualityCandidatesIgnoreDraftsOnly() throws Exception {
        String edgeSql = selectSql(GeneralInspectionTaskMapper.class.getMethod(
                "selectPendingReminderCandidateIds", Long.class, LocalDateTime.class, LocalDateTime.class));
        String qualitySql = selectSql(QualityWeeklyReminderSettingMapper.class.getMethod(
                "selectEnabledSettings", LocalDate.class));

        assertThat(edgeSql).contains("t.assignee_id IS NOT NULL");
        assertThat(qualitySql).contains(
                "FROM quality_weekly_inspection inspection",
                "inspection.week_start = #{weekStart}",
                "inspection.status <> 'DRAFT'");
    }

    private String selectSql(Method method) {
        Select select = method.getAnnotation(Select.class);
        assertThat(select).as("@Select on %s", method).isNotNull();
        return String.join(" ", Arrays.asList(select.value())).replaceAll("\\s+", " ").trim();
    }
}
