package com.example.siteplatform.seal.service;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public record SealFileContent(Resource resource, String fileName, MediaType mediaType, boolean inline) {
}
