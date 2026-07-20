package com.example.siteplatform.quality.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.quality.dto.QualityIssueCreateRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;

class QualityIssueServiceTest {
    @Test
    void createRequiresAtLeastOneProblemPhoto() {
        QualityIssueService service = new QualityIssueService();
        QualityIssueCreateRequest request = new QualityIssueCreateRequest();
        request.setProjectId(1L);
        request.setTitle("防水层收口不完整");
        request.setDeadline(LocalDate.now().plusDays(3));
        request.setPhotoFileIds(Collections.emptyList());

        assertThrows(BusinessException.class, () -> service.createIssue(request, new SysUser()));
    }
}
