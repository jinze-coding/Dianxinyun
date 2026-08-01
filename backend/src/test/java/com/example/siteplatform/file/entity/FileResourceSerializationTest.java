package com.example.siteplatform.file.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileResourceSerializationTest {

    @Test
    void hidesServerStorageLocationsFromApiPayload() {
        FileResource file = new FileResource();
        file.setId(1L);
        file.setFileName("方案.pdf");
        file.setFilePath("/srv/private/uploads/internal.pdf");
        file.setStorageKey("project-documents/1/internal.pdf");
        file.setStorageProvider("local");

        JsonNode json = new ObjectMapper().valueToTree(file);

        assertFalse(json.has("filePath"));
        assertFalse(json.has("storageKey"));
        assertTrue(json.has("storageProvider"));
    }
}
