package com.example.siteplatform.file.constant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStatusTest {

    @Test
    void normalizesKnownEnglishAndChineseAliases() {
        assertEquals(FileStatus.UPLOADED, FileStatus.normalize("已上传"));
        assertEquals(FileStatus.PENDING_CONFIRM, FileStatus.normalize("pending-confirm"));
        assertEquals(FileStatus.ARCHIVED, FileStatus.normalize("archived"));
        assertTrue(FileStatus.isArchived("已归档"));
    }

    @Test
    void compatibleQueryValuesCoverCanonicalAndHistoricalValues() {
        assertEquals(
                List.of("UPLOADED", "uploaded", "已上传"),
                FileStatus.compatibleQueryValues(FileStatus.UPLOADED));
        assertTrue(FileStatus.compatibleQueryValues("待确认").contains("PENDING_CONFIRM"));
        assertTrue(FileStatus.compatibleQueryValues("已归档").contains("ARCHIVED"));
    }

    @Test
    void distinguishesSupportedAndUnknownValues() {
        assertTrue(FileStatus.isSupported("已上传"));
        assertFalse(FileStatus.isSupported("ACTIVE"));
        assertFalse(FileStatus.isSupported(""));
        assertFalse(FileStatus.isSupported(null));
    }
}
