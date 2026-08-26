package com.example.siteplatform.inspection.general.service;

import com.example.siteplatform.inspection.general.entity.GeneralInspectionEventOutbox;
import com.example.siteplatform.inspection.general.mapper.GeneralInspectionEventOutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class SpringGeneralInspectionEventPublisherTest {

    @Test
    void eventKeyIncludesBusinessAndVersionAndDuplicateDoesNotBlockBusiness() {
        GeneralInspectionEventOutboxMapper mapper = mock(GeneralInspectionEventOutboxMapper.class);
        GeneralInspectionEventOutbox[] captured = new GeneralInspectionEventOutbox[1];
        doAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            throw new DuplicateKeyException("duplicate");
        }).when(mapper).insert(any(GeneralInspectionEventOutbox.class));
        SpringGeneralInspectionEventPublisher publisher =
                new SpringGeneralInspectionEventPublisher(mapper, new ObjectMapper());

        assertThatCode(() -> publisher.publish(new GeneralInspectionDomainEvent(
                "RECTIFICATION_REVIEW_PENDING", 2L, "RECTIFICATION", 8L,
                LocalDateTime.of(2026, 8, 26, 12, 0), Map.of("version", 3))))
                .doesNotThrowAnyException();

        assertThat(captured[0].getEventKey())
                .isEqualTo("RECTIFICATION_REVIEW_PENDING:RECTIFICATION:8:3");
        assertThat(captured[0].getStatus()).isEqualTo("PENDING");
        assertThat(captured[0].getPayloadJson()).contains("\"version\":3");
    }
}
