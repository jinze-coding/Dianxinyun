package com.example.siteplatform.quality.dto;

import com.example.siteplatform.quality.controller.QualityIssueController;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityRequestValidationTest {

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
    void createRequestRejectsMissingOversizedAndUnsupportedValues() {
        QualityIssueCreateRequest request = new QualityIssueCreateRequest();
        request.setProjectId(-1L);
        request.setRequestKey("x".repeat(101));
        request.setTitle(" ");
        request.setLocation("x".repeat(201));
        request.setDescription("x".repeat(1001));
        request.setSeverity("CRITICAL");
        request.setAssigneeId(0L);
        request.setDeadline(LocalDate.now().minusDays(1));
        request.setPhotoFileIds(Collections.emptyList());

        Set<String> paths = paths(validator.validate(request));

        assertTrue(paths.containsAll(Set.of(
                "projectId", "requestKey", "title", "location", "description",
                "severity", "assigneeId", "deadline", "photoFileIds")));
    }

    @Test
    void createRequestRejectsInvalidPhotoIdsAndExcessivePhotoCount() {
        QualityIssueCreateRequest request = new QualityIssueCreateRequest();
        request.setProjectId(9L);
        request.setTitle("防水层收口不完整");
        request.setPhotoFileIds(IntStream.range(0, 21)
                .mapToObj(index -> index == 0 ? 0L : (long) index)
                .toList());

        Set<String> paths = paths(validator.validate(request));

        assertTrue(paths.contains("photoFileIds"));
        assertTrue(paths.stream().anyMatch(path -> path.startsWith("photoFileIds[0]")));
    }

    @Test
    void rectificationReviewAssignAndVoidRequestsRespectDatabaseBounds() {
        QualityRectificationRequest rectification = new QualityRectificationRequest();
        rectification.setDescription(" ");
        rectification.setPhotoFileIds(List.of(-1L));

        QualityReviewRequest review = new QualityReviewRequest();
        review.setComment("x".repeat(1001));
        review.setPhotoFileIds(List.of(0L));

        QualityAssignRequest assign = new QualityAssignRequest();
        assign.setAssigneeId(-1L);
        assign.setDeadline(LocalDate.now().minusDays(1));
        assign.setComment("x".repeat(1001));

        QualityVoidRequest voidRequest = new QualityVoidRequest();
        voidRequest.setComment(" ");

        Set<String> rectificationPaths = paths(validator.validate(rectification));
        Set<String> reviewPaths = paths(validator.validate(review));
        Set<String> assignPaths = paths(validator.validate(assign));
        Set<String> voidPaths = paths(validator.validate(voidRequest));

        assertTrue(rectificationPaths.contains("description"));
        assertTrue(rectificationPaths.stream().anyMatch(path -> path.startsWith("photoFileIds[0]")));
        assertTrue(reviewPaths.containsAll(Set.of("passed", "comment")));
        assertTrue(reviewPaths.stream().anyMatch(path -> path.startsWith("photoFileIds[0]")));
        assertTrue(assignPaths.containsAll(Set.of("assigneeId", "deadline", "comment")));
        assertTrue(voidPaths.contains("comment"));
    }

    @Test
    void everyQualityWriteEndpointEnablesRequestBodyValidation() throws NoSuchMethodException {
        assertValidatedBody("createIssue", QualityIssueCreateRequest.class);
        assertValidatedBody("submitRectification", QualityRectificationRequest.class);
        assertValidatedBody("reviewIssue", QualityReviewRequest.class);
        assertValidatedBody("assignIssue", QualityAssignRequest.class);
        assertValidatedBody("voidIssue", QualityVoidRequest.class);
    }

    private void assertValidatedBody(String methodName, Class<?> requestType) throws NoSuchMethodException {
        Class<?>[] parameterTypes = "createIssue".equals(methodName)
                ? new Class<?>[]{requestType, String.class}
                : new Class<?>[]{Long.class, requestType, String.class};
        Method method = QualityIssueController.class.getDeclaredMethod(methodName, parameterTypes);
        Parameter requestParameter = java.util.Arrays.stream(method.getParameters())
                .filter(parameter -> parameter.getType().equals(requestType))
                .findFirst()
                .orElseThrow();
        assertTrue(requestParameter.isAnnotationPresent(Valid.class));
    }

    private Set<String> paths(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(item -> item.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}
