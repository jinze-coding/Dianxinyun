package com.example.siteplatform.document.service;

import org.springframework.core.io.Resource;

public record ProjectDocumentContent(Resource resource, String fileName, String mimeType, long fileSize) {
}
