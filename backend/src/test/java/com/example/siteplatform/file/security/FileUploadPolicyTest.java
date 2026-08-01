package com.example.siteplatform.file.security;

import com.example.siteplatform.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileUploadPolicyTest {

    @Test
    void acceptsRealRasterPhotoForWorkflowUpload() {
        MockMultipartFile photo = new MockMultipartFile(
                "file", "现场照片.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});

        assertDoesNotThrow(() ->
                FileUploadPolicy.validateBusinessUpload(photo, "QUALITY_PENDING"));
    }

    @Test
    void rejectsPdfDisguisedAsWorkflowPhoto() {
        MockMultipartFile fakePhoto = new MockMultipartFile(
                "file", "现场照片.jpg", "image/jpeg", "%PDF-1.7".getBytes());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                FileUploadPolicy.validateBusinessUpload(fakePhoto, "INSPECTION_RECORD"));

        assertEquals(400, exception.getCode());
    }

    @Test
    void rejectsActiveSvgAndHtmlEvenWhenClientMimeIsSpoofed() {
        MockMultipartFile svg = new MockMultipartFile(
                "file", "图纸.svg", "application/octet-stream", "<svg></svg>".getBytes());
        MockMultipartFile htmlAsText = new MockMultipartFile(
                "file", "说明.txt", "text/plain", "<script>alert(1)</script>".getBytes());

        assertThrows(BusinessException.class, () ->
                FileUploadPolicy.validateProjectDocument(svg));
        assertThrows(BusinessException.class, () ->
                FileUploadPolicy.validateProjectDocument(htmlAsText));
    }

    @Test
    void acceptsPdfAndRealXlsxSignature() {
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "方案.pdf", "application/pdf", "%PDF-1.7".getBytes());
        MockMultipartFile xlsx = new MockMultipartFile(
                "file", "电箱模板.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{0x50, 0x4b, 0x03, 0x04});

        assertDoesNotThrow(() -> FileUploadPolicy.validateProjectDocument(pdf));
        assertDoesNotThrow(() -> FileUploadPolicy.validateElectricBoxImport(xlsx));
    }

    @Test
    void rejectsLegacyXlsForXlsxOnlyImport() {
        MockMultipartFile xls = new MockMultipartFile(
                "file", "电箱模板.xls", "application/vnd.ms-excel",
                new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
                        (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1});

        assertThrows(BusinessException.class, () ->
                FileUploadPolicy.validateElectricBoxImport(xls));
    }

    @Test
    void rejectsOversizedPhotoBeforeReadingStream() {
        MultipartFile photo = mock(MultipartFile.class);
        when(photo.isEmpty()).thenReturn(false);
        when(photo.getSize()).thenReturn(FileUploadPolicy.MAX_IMAGE_BYTES + 1);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                FileUploadPolicy.validateBusinessUpload(photo, "QUALITY_PENDING"));

        assertEquals(413, exception.getCode());
    }

    @Test
    void sanitizesClientPathAndNeverInlinesActiveFormats() {
        assertEquals("方案.pdf",
                FileUploadPolicy.safeOriginalFileName("C:\\fakepath\\方案.pdf"));
        assertFalse(FileUploadPolicy.canPreviewInline("legacy.svg"));
        assertEquals(MediaType.APPLICATION_OCTET_STREAM,
                FileUploadPolicy.responseMediaType("legacy.svg"));
    }
}
