package com.example.siteplatform.workflow.mapper;

import com.example.siteplatform.seal.mapper.SealApplicationMapper;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowApprovalCasContractTest {

    @Test
    void taskDecisionIsBoundToAssigneePendingStatusAndExpectedVersion() throws Exception {
        Method method = WorkflowApprovalTaskMapper.class.getMethod("decide", Long.class, Integer.class,
                String.class, Long.class, String.class, String.class, LocalDateTime.class);
        String sql = normalizedSql(method);

        assertTrue(sql.contains("assignee_user_id = #{userId}"));
        assertTrue(sql.contains("status = 'PENDING'"));
        assertTrue(sql.contains("version = #{expectedVersion}"));
        assertTrue(sql.contains("version = version + 1"));
    }

    @Test
    void instanceAndApplicationDecisionsEachUsePendingStateAndExpectedVersionCas() throws Exception {
        Method instanceDecision = WorkflowApprovalInstanceMapper.class.getMethod("decide", Long.class,
                Integer.class, String.class, Long.class, String.class, String.class, LocalDateTime.class);
        Method applicationDecision = SealApplicationMapper.class.getMethod("decide", Long.class, Integer.class,
                String.class, Long.class, String.class, String.class, LocalDateTime.class);

        String instanceSql = normalizedSql(instanceDecision);
        String applicationSql = normalizedSql(applicationDecision);
        assertTrue(instanceSql.contains("status = 'PENDING'"));
        assertTrue(instanceSql.contains("version = #{expectedVersion}"));
        assertTrue(instanceSql.contains("version = version + 1"));
        assertTrue(applicationSql.contains("status = 'PENDING_APPROVAL'"));
        assertTrue(applicationSql.contains("version = #{expectedVersion}"));
        assertTrue(applicationSql.contains("version = version + 1"));
    }

    private String normalizedSql(Method method) {
        Update update = method.getAnnotation(Update.class);
        return String.join(" ", Arrays.stream(update.value())
                        .map(String::trim)
                        .toList())
                .replaceAll("\\s+", " ");
    }
}
