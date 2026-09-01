package com.example.siteplatform.document.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentSceneServiceTest {

    @Test
    void sceneIsOpaqueEncryptedAndQrDoesNotExposeBusinessIds() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        DocumentSceneService service = new DocumentSceneService("unit-test-document-scene-key-more-than-32-bytes", environment);

        String scene = service.newScene();
        String encrypted = service.encrypt(scene);
        String svg = service.qrSvg(scene);

        assertEquals(scene, service.decrypt(encrypted));
        assertEquals(64, service.digest(scene).length());
        assertTrue(svg.contains("<svg"));
        assertFalse(svg.contains(scene));
        assertFalse(scene.equals("123456"));
    }

    @Test
    void productionRefusesMissingDedicatedKey() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class, () -> new DocumentSceneService("", environment));
    }
}
