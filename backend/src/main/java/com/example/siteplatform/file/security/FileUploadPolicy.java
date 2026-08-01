package com.example.siteplatform.file.security;

import com.example.siteplatform.common.BusinessException;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Server-side upload policy. Browser accept attributes and client MIME values
 * are advisory only; authorization callers must invoke this policy before
 * persisting the stream.
 */
public final class FileUploadPolicy {
    public static final long MAX_IMAGE_BYTES = 15L * 1024 * 1024;
    public static final long MAX_DOCUMENT_BYTES = 50L * 1024 * 1024;
    public static final long MAX_IMPORT_BYTES = 10L * 1024 * 1024;

    private static final int PREFIX_BYTES = 4096;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif");
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "wps", "et", "dps", "rtf",
            "txt", "md", "csv", "dwg", "dxf", "ofd", "zip", "rar", "7z");
    private static final Set<String> ACTIVE_MIME_TYPES = Set.of(
            "text/html", "application/xhtml+xml", "image/svg+xml",
            "application/javascript", "text/javascript",
            "application/x-httpd-php", "application/x-sh", "application/x-msdownload");
    private static final Map<String, MediaType> RESPONSE_MEDIA_TYPES = Map.ofEntries(
            Map.entry("pdf", MediaType.APPLICATION_PDF),
            Map.entry("jpg", MediaType.IMAGE_JPEG),
            Map.entry("jpeg", MediaType.IMAGE_JPEG),
            Map.entry("png", MediaType.IMAGE_PNG),
            Map.entry("gif", MediaType.IMAGE_GIF),
            Map.entry("webp", MediaType.parseMediaType("image/webp")),
            Map.entry("bmp", MediaType.parseMediaType("image/bmp")),
            Map.entry("heic", MediaType.parseMediaType("image/heic")),
            Map.entry("heif", MediaType.parseMediaType("image/heif")),
            Map.entry("doc", MediaType.parseMediaType("application/msword")),
            Map.entry("docx", MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Map.entry("xls", MediaType.parseMediaType("application/vnd.ms-excel")),
            Map.entry("xlsx", MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            Map.entry("ppt", MediaType.parseMediaType("application/vnd.ms-powerpoint")),
            Map.entry("pptx", MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation")),
            Map.entry("txt", MediaType.TEXT_PLAIN),
            Map.entry("md", MediaType.parseMediaType("text/markdown")),
            Map.entry("csv", MediaType.parseMediaType("text/csv")),
            Map.entry("zip", MediaType.parseMediaType("application/zip")),
            Map.entry("rar", MediaType.parseMediaType("application/vnd.rar")),
            Map.entry("7z", MediaType.parseMediaType("application/x-7z-compressed"))
    );

    private FileUploadPolicy() {
    }

    public static void validateProjectDocument(MultipartFile file) {
        validate(file, DOCUMENT_EXTENSIONS, MAX_DOCUMENT_BYTES, "工程资料");
    }

    public static void validateBusinessUpload(MultipartFile file, String businessType) {
        String normalized = businessType == null ? "" : businessType.trim().toUpperCase(Locale.ROOT);
        boolean workflowPhoto = (normalized.startsWith("QUALITY_") && !"QUALITY_DOCUMENT".equals(normalized))
                || normalized.startsWith("INSPECTION_");
        validate(file,
                workflowPhoto ? IMAGE_EXTENSIONS : DOCUMENT_EXTENSIONS,
                workflowPhoto ? MAX_IMAGE_BYTES : MAX_DOCUMENT_BYTES,
                workflowPhoto ? "业务照片" : "业务附件");
    }

    public static void validateElectricBoxImport(MultipartFile file) {
        validate(file, Set.of("xlsx"), MAX_IMPORT_BYTES, "电箱导入文件");
    }

    public static String safeOriginalFileName(String rawName) {
        if (rawName == null) throw new BusinessException("文件名不能为空");
        String normalized = rawName.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        if (normalized.isEmpty()) throw new BusinessException("文件名不能为空");
        if (normalized.length() > 200) throw new BusinessException("文件名不能超过200个字符");
        return normalized;
    }

    public static String extensionOf(String... names) {
        if (names == null) return "";
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            String normalized = name.replace('\\', '/');
            int slash = normalized.lastIndexOf('/');
            int dot = normalized.lastIndexOf('.');
            if (dot > slash && dot < normalized.length() - 1) {
                return normalized.substring(dot + 1).toLowerCase(Locale.ROOT);
            }
        }
        return "";
    }

    public static boolean canPreviewInline(String... names) {
        String extension = extensionOf(names);
        return "pdf".equals(extension) || IMAGE_EXTENSIONS.contains(extension);
    }

    public static MediaType responseMediaType(String... names) {
        return RESPONSE_MEDIA_TYPES.getOrDefault(extensionOf(names), MediaType.APPLICATION_OCTET_STREAM);
    }

    private static void validate(MultipartFile file, Set<String> allowedExtensions,
                                 long maxBytes, String label) {
        if (file == null || file.isEmpty()) throw new BusinessException(label + "不能为空");
        if (file.getSize() > maxBytes) {
            throw BusinessException.of(413, label + "不能超过" + (maxBytes / 1024 / 1024) + "MB");
        }
        String originalName = safeOriginalFileName(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        if (!allowedExtensions.contains(extension)) {
            throw BusinessException.of(400, label + "格式不支持：" + extension);
        }
        String declaredType = file.getContentType() == null
                ? "" : file.getContentType().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (ACTIVE_MIME_TYPES.contains(declaredType)) {
            throw BusinessException.of(400, label + "内容类型不安全");
        }
        byte[] prefix = readPrefix(file);
        if (!matchesSignature(extension, prefix)) {
            throw BusinessException.of(400, label + "扩展名与文件内容不一致");
        }
    }

    private static byte[] readPrefix(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return input.readNBytes(PREFIX_BYTES);
        } catch (IOException exception) {
            throw new BusinessException("文件内容读取失败");
        }
    }

    private static boolean matchesSignature(String extension, byte[] bytes) {
        return switch (extension) {
            case "jpg", "jpeg" -> startsWith(bytes, 0xff, 0xd8, 0xff);
            case "png" -> startsWith(bytes, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
            case "gif" -> startsWithAscii(bytes, "GIF87a") || startsWithAscii(bytes, "GIF89a");
            case "webp" -> startsWithAscii(bytes, "RIFF")
                    && bytes.length >= 12
                    && asciiAt(bytes, 8, "WEBP");
            case "bmp" -> startsWithAscii(bytes, "BM");
            case "heic", "heif" -> isIsoBaseMediaImage(bytes);
            case "pdf" -> startsWithAscii(bytes, "%PDF-");
            case "docx", "xlsx", "pptx", "ofd", "zip" -> isZip(bytes);
            case "doc", "xls", "ppt" -> isOle(bytes);
            case "wps", "et", "dps" -> isOle(bytes) || isZip(bytes);
            case "rtf" -> startsWithAscii(bytes, "{\\rtf");
            case "rar" -> startsWith(bytes, 0x52, 0x61, 0x72, 0x21, 0x1a, 0x07);
            case "7z" -> startsWith(bytes, 0x37, 0x7a, 0xbc, 0xaf, 0x27, 0x1c);
            case "dwg" -> startsWithAscii(bytes, "AC10");
            case "txt", "md", "csv", "dxf" -> isSafeText(bytes);
            default -> false;
        };
    }

    private static boolean isZip(byte[] bytes) {
        return startsWith(bytes, 0x50, 0x4b, 0x03, 0x04)
                || startsWith(bytes, 0x50, 0x4b, 0x05, 0x06)
                || startsWith(bytes, 0x50, 0x4b, 0x07, 0x08);
    }

    private static boolean isOle(byte[] bytes) {
        return startsWith(bytes, 0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1);
    }

    private static boolean isIsoBaseMediaImage(byte[] bytes) {
        if (bytes.length < 12 || !asciiAt(bytes, 4, "ftyp")) return false;
        String brand = new String(bytes, 8, 4, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        return Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1")
                .contains(brand);
    }

    private static boolean isSafeText(byte[] bytes) {
        if (bytes.length == 0) return false;
        int controls = 0;
        for (byte value : bytes) {
            int unsigned = value & 0xff;
            if (unsigned == 0) return false;
            if (unsigned < 0x20 && unsigned != '\n' && unsigned != '\r' && unsigned != '\t') {
                controls++;
            }
        }
        if (controls > Math.max(1, bytes.length / 100)) return false;
        String prefix = new String(bytes, StandardCharsets.ISO_8859_1)
                .stripLeading().toLowerCase(Locale.ROOT);
        return Arrays.stream(new String[]{
                        "<!doctype html", "<html", "<script", "<svg", "<?php", "<%@"
                })
                .noneMatch(prefix::startsWith);
    }

    private static boolean startsWithAscii(byte[] bytes, String value) {
        return asciiAt(bytes, 0, value);
    }

    private static boolean asciiAt(byte[] bytes, int offset, String value) {
        byte[] expected = value.getBytes(StandardCharsets.ISO_8859_1);
        if (bytes.length < offset + expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if (bytes[offset + i] != expected[i]) return false;
        }
        return true;
    }

    private static boolean startsWith(byte[] bytes, int... expected) {
        if (bytes.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[i] & 0xff) != expected[i]) return false;
        }
        return true;
    }
}
