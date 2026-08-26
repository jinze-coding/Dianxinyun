package com.example.siteplatform.quality.controller;

import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QualityIssueControllerRetirementTest {

    @Test
    void legacySingleIssuePostReturnsHttp410() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> new QualityIssueController().createIssue(null));

        assertEquals(410, error.getCode());
    }

    @Test
    void legacySingleIssuePostReturnsHttp410BeforeBodyParsingOrValidation() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new QualityIssueController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        for (String body : new String[]{"", "{}", "{malformed"}) {
            mockMvc.perform(post("/api/v1/quality/issues")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.code").value(410));
        }
    }
}
