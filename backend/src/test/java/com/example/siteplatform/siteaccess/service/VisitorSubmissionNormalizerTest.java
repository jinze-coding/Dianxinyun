package com.example.siteplatform.siteaccess.service;

import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.siteaccess.dto.SiteVisitPersonRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisitorSubmissionNormalizerTest {
    @Test
    void skipsBlankCompanionAndKeepsPartiallyFilledOptionalCompanion() {
        SiteVisitPersonRequest blank = new SiteVisitPersonRequest();
        SiteVisitPersonRequest companyOnly = new SiteVisitPersonRequest();
        companyOnly.setPersonCompany("同行单位");

        var result = VisitorSubmissionNormalizer.normalize(
                "主单位", "张三", "13800138000", List.of(blank, companyOnly),
                "OTHER", null, null);

        assertThat(result.people()).hasSize(2);
        assertThat(result.people().get(1).personCompany()).isEqualTo("同行单位");
        assertThat(result.people().get(1).personName()).isNull();
        assertThat(result.people().get(1).personPhone()).isNull();
    }

    @Test
    void validatesOptionalCompanionPhoneWhenProvided() {
        SiteVisitPersonRequest companion = new SiteVisitPersonRequest();
        companion.setPersonName("李四");
        companion.setPersonPhone("123");

        assertThatThrownBy(() -> VisitorSubmissionNormalizer.normalize(
                "主单位", "张三", "13800138000", List.of(companion),
                "OTHER", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同行人员手机号码格式不正确");
    }
}
