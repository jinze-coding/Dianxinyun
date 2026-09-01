package com.example.siteplatform.inspection.general;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.example.siteplatform.inspection.general.mapper.EdgeInspectionWorkspaceMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class EdgeInspectionWorkspaceMapperContractTest {

    @Test
    void aggregateSqlParsesAndKeepsPointTaskAndSheetCountsDistinct() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(EdgeInspectionWorkspaceMapper.class);
        assertThat(configuration.hasStatement(
                EdgeInspectionWorkspaceMapper.class.getName() + ".selectSummary")).isTrue();

        Select select = EdgeInspectionWorkspaceMapper.class
                .getMethod("selectSummary", Long.class, Long.class,
                        java.time.LocalDate.class, java.time.LocalDateTime.class)
                .getAnnotation(Select.class);
        String sql = String.join(" ", Arrays.asList(select.value()));
        assertThat(sql).contains(
                "p.status = 'ACTIVE'",
                "t.occurrence_date = #{today}",
                "t.due_time < #{now}",
                "COUNT(DISTINCT r.task_id)",
                "r.status IN ('PENDING', 'REJECTED')",
                "r.status = 'COMPLETED'",
                "rt.point_type_code IS NOT NULL");
    }
}
