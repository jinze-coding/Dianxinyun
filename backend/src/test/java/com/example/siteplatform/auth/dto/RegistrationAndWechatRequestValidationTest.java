package com.example.siteplatform.auth.dto;

import com.example.siteplatform.registration.dto.RegistrationStatusRequest;
import com.example.siteplatform.registration.dto.RegistrationSubmitRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationAndWechatRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void registrationAliasesAndProjectIdsAreValidated() {
        RegistrationSubmitRequest request = new RegistrationSubmitRequest();
        request.setUsername("bad name");
        request.setPassword("short");
        request.setRealName("申请人");
        request.setPhone("123");
        request.setReason("x".repeat(501));
        request.setDesiredProjectIds(List.of(-1L));

        Set<String> paths = paths(validator.validate(request));

        assertTrue(paths.contains("username"));
        assertTrue(paths.contains("password"));
        assertTrue(paths.contains("phone"));
        assertTrue(paths.contains("reason"));
        assertTrue(paths.contains("desiredProjectIds[0].<list element>"));
    }

    @Test
    void wechatLoginRequiresNonBlankBoundedCode() {
        WechatSessionRequest request = new WechatSessionRequest();
        request.setCode(" ");

        Set<String> paths = paths(validator.validate(request));

        assertEquals(Set.of("code"), paths);
    }

    @Test
    void phoneMatchRequiresSessionValidPhoneAndScene() {
        WechatPhoneRequest request = new WechatPhoneRequest();
        request.setPhone("123");

        Set<String> paths = paths(validator.validate(request));

        assertTrue(paths.contains("wechatSessionToken"));
        assertTrue(paths.contains("phone"));
        assertTrue(paths.contains("scene"));
    }

    @Test
    void statusTokenAliasesAreLengthLimited() {
        RegistrationStatusRequest request = new RegistrationStatusRequest();
        request.setQueryToken("x".repeat(129));

        Set<String> paths = paths(validator.validate(request));

        assertEquals(Set.of("queryToken"), paths);
    }

    private Set<String> paths(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(item -> item.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}
