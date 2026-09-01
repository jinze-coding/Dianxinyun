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
    void projectProfileOnlyAcceptsJpegPngAndWebp() {
        MockMultipartFile jpeg = new MockMultipartFile(
                "file", "效果图.jpeg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
        MockMultipartFile gif = new MockMultipartFile(
                "file", "效果图.gif", "image/gif", "GIF89a".getBytes());

        assertDoesNotThrow(() ->
                FileUploadPolicy.validateBusinessUpload(jpeg, "PROJECT_PROFILE_IMAGE_PENDING"));
        assertThrows(BusinessException.class, () ->
                FileUploadPolicy.validateBusinessUpload(gif, "PROJECT_PROFILE_IMAGE_PENDING"));
    }

    @Test
    void projectRouteImageUsesTheSameFifteenMegabyteSafeRasterPolicy() {
        MockMultipartFile webp = new MockMultipartFile(
                "file", "路线图.webp", "image/webp",
                new byte[]{0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50});
        MockMultipartFile gif = new MockMultipartFile(
                "file", "路线图.gif", "image/gif", "GIF89a".getBytes());

        assertDoesNotThrow(() ->
                FileUploadPolicy.validateBusinessUpload(webp, "PROJECT_ROUTE_IMAGE_PENDING"));
        assertThrows(BusinessException.class, () ->
                FileUploadPolicy.validateBusinessUpload(gif, "PROJECT_ROUTE_IMAGE_PENDING"));
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
    void circulationAcceptsIfcRvtAndDgnSignatures() {
        MockMultipartFile ifc = new MockMultipartFile(
                "file", "模型.ifc", "application/octet-stream",
                "ISO-10303-21;\nHEADER;\nENDSEC;".getBytes());
        MockMultipartFile rvt = new MockMultipartFile(
                "file", "模型.rvt", "application/octet-stream",
                new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
                        (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1,
                        'B', 'a', 's', 'i', 'c', 'F', 'i', 'l', 'e', 'I', 'n', 'f', 'o'});
        MockMultipartFile dgn = new MockMultipartFile(
                "file", "总图.dgn", "application/octet-stream",
                new byte[]{0x08, 0x09, (byte) 0xfe, 0x02, 0x00, 0x00});

        assertDoesNotThrow(() -> FileUploadPolicy.validateCirculationDocument(ifc));
        assertDoesNotThrow(() -> FileUploadPolicy.validateCirculationDocument(rvt));
        assertDoesNotThrow(() -> FileUploadPolicy.validateCirculationDocument(dgn));
    }

    @Test
    void circulationRejectsSpoofedBimFilesAndAllowsTwoHundredMegabytes() {
        MockMultipartFile fakeIfc = new MockMultipartFile(
                "file", "模型.ifc", "application/octet-stream", "<script>x</script>".getBytes());
        MockMultipartFile renamedDocAsRvt = new MockMultipartFile(
                "file", "普通文档.rvt", "application/octet-stream",
                new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
                        (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1});
        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(FileUploadPolicy.MAX_CIRCULATION_DOCUMENT_BYTES + 1);

        assertThrows(BusinessException.class, () -> FileUploadPolicy.validateCirculationDocument(fakeIfc));
        assertThrows(BusinessException.class, () -> FileUploadPolicy.validateCirculationDocument(renamedDocAsRvt));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> FileUploadPolicy.validateCirculationDocument(oversized));
        assertEquals(413, exception.getCode());
        assertEquals(200L * 1024 * 1024, FileUploadPolicy.MAX_CIRCULATION_DOCUMENT_BYTES);
    }

    @Test
    void receiptSignatureOnlyAcceptsSmallPng() {
        MockMultipartFile png = new MockMultipartFile(
                "signature", "签名.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        MockMultipartFile jpeg = new MockMultipartFile(
                "signature", "签名.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});

        assertDoesNotThrow(() -> FileUploadPolicy.validateReceiptSignature(png));
        assertThrows(BusinessException.class, () -> FileUploadPolicy.validateReceiptSignature(jpeg));
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
