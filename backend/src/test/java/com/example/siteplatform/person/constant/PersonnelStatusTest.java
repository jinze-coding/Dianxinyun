package com.example.siteplatform.person.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersonnelStatusTest {
    @Test
    void normalizesLegacyAndCanonicalStatuses() {
        assertEquals(PersonnelStatus.WAIT_EDUCATION, PersonnelStatus.normalize("待教育"));
        assertEquals(PersonnelStatus.EDUCATED, PersonnelStatus.normalize("已教育"));
        assertEquals(PersonnelStatus.LEFT, PersonnelStatus.normalize("LEFT"));
        assertEquals("已离场", PersonnelStatus.label("LEFT"));
    }
}
