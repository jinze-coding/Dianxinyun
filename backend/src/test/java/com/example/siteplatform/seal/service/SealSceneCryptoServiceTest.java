package com.example.siteplatform.seal.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SealSceneCryptoServiceTest {

    @Test
    void encryptsSceneWithDedicatedAuthenticatedEnvelopeAndStableDigest() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "test");
        environment.setActiveProfiles("test");
        SealSceneCryptoService crypto = new SealSceneCryptoService("0123456789abcdef0123456789abcdef", environment);

        String first = crypto.encrypt("seal-scene-42");
        String second = crypto.encrypt("seal-scene-42");

        assertNotEquals(first, second);
        assertEquals("seal-scene-42", crypto.decrypt(first));
        assertEquals(64, crypto.digest("seal-scene-42").length());
        assertNotEquals("seal-scene-42", first);
    }

    @Test
    void productionRejectsMissingOrWeakKey() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThrows(IllegalStateException.class, () -> new SealSceneCryptoService("", environment));
        assertThrows(IllegalStateException.class, () -> new SealSceneCryptoService("too-short", environment));
    }
}
