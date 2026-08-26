package com.example.siteplatform.inspection.general;

import com.example.siteplatform.inspection.general.dto.EdgeInspectionSettingRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EdgeInspectionSettingRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void monthlyDayAllowsThirtyOneButRejectsThirtyTwo() {
        EdgeInspectionSettingRequest request = new EdgeInspectionSettingRequest();
        request.setMonthDay(31);
        assertThat(validator.validateProperty(request, "monthDay")).isEmpty();

        request.setMonthDay(32);
        assertThat(validator.validateProperty(request, "monthDay")).isNotEmpty();
    }
}
