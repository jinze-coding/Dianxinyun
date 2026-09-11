package com.example.siteplatform.seal.service;

import com.example.siteplatform.seal.entity.SealApplication;
import com.example.siteplatform.seal.entity.SealApplicationItem;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class SealLedgerWordRendererTest {
    private final SealLedgerWordRenderer renderer = new SealLedgerWordRenderer();

    @Test
    void mapsFilesToSeparateRowsAndPreservesAllOtherTemplateParts() throws Exception {
        var first = application(1L, "张三", "李四");
        var second = application(2L, "王五", "赵六");
        first.setApplicantPhone("18911112222");
        first.setPurpose("仅在系统详情中显示的事由");
        first.setCompanyName("不替换模板抬头的公司");
        byte[] bytes = renderer.render(List.of(first, second), Map.of(
                1L, List.of(item("施工合同", 2), item("材料报审表", 3)),
                2L, List.of(item("工程联系单", 1))));
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            var table = document.getTables().get(0);
            assertEquals(4, table.getNumberOfRows());
            assertEquals(List.of("1", "2026-\n09-11", "施工合同", "2", "测试项目部", "张三", "李四"),
                    table.getRow(1).getTableCells().stream().map(cell -> cell.getText()).toList());
            assertEquals("2", table.getRow(2).getCell(0).getText());
            assertEquals("材料报审表", table.getRow(2).getCell(2).getText());
            assertEquals("3", table.getRow(2).getCell(3).getText());
            assertEquals("王五", table.getRow(3).getCell(5).getText());
            assertEquals("赵六", table.getRow(3).getCell(6).getText());
            assertTrue(table.getRow(0).isRepeatHeader());
            assertTrue(table.getRow(1).isCantSplitRow());
            assertEquals(2, table.getRow(0).getCell(4).getCTTc().getTcPr().getGridSpan().getVal().intValue());
            assertEquals("landscape", document.getDocument().getBody().getSectPr().getPgSz().getOrient().toString());
        }
        byte[] reference = new ClassPathResource("templates/seal-ledger.docx").getContentAsByteArray();
        assertEquals("db7474731fa2324484a0efa3805e9bb6c1a04dd36b633f976ff754041a60f3ce",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(reference)));
        var sourceParts = unzip(reference);
        var actualParts = unzip(bytes);
        assertEquals(sourceParts.keySet(), actualParts.keySet());
        for (String name : sourceParts.keySet()) {
            if (!name.equals("word/document.xml")) assertArrayEquals(sourceParts.get(name), actualParts.get(name), name);
        }
        String xml = new String(actualParts.get("word/document.xml"), StandardCharsets.UTF_8);
        assertTrue(xml.contains("上海建工"));
        assertTrue(xml.contains("印章使用登记台账"));
        assertFalse(xml.contains("18911112222"));
        assertFalse(xml.contains(first.getPurpose()));
        assertFalse(xml.contains(first.getCompanyName()));
        artifact("seal-ledger-single.docx", bytes);
    }

    @Test
    void emptyLedgerKeepsHeaderWithoutPaddingRows() throws Exception {
        byte[] bytes = renderer.render(List.of(), Map.of());
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            assertEquals(1, document.getTables().get(0).getNumberOfRows());
            assertTrue(document.getParagraphs().stream().anyMatch(p -> p.getText().contains("编号")));
        }
        artifact("seal-ledger-empty.docx", bytes);
    }

    @Test
    void historicalNullsStayBlankAndTextCannotCreateWordMarkupOrFields() throws Exception {
        var historical = new SealApplication();
        historical.setId(1L);
        var named = application(2L, "测试人", "审批人");
        String name = "=合同 & <技术要求>\n第二行\u0001";
        byte[] bytes = renderer.render(List.of(historical, named), Map.of(2L, List.of(item(name, null))));
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            var rows = document.getTables().get(0).getRows();
            assertEquals(3, rows.size());
            assertEquals(List.of("1", "", "", "", "", "", ""),
                    rows.get(1).getTableCells().stream().map(cell -> cell.getText()).toList());
            assertEquals("=合同 & <技术要求>\n第二行", rows.get(2).getCell(2).getText());
            assertEquals("", rows.get(2).getCell(3).getText());
            assertEquals(0, document.getDocument().getBody().selectPath(
                    "declare namespace w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'; .//w:instrText").length);
        }
    }

    @Test
    void longNamesAndMultiplePagesKeepAllRowsAndTemplateTypography() throws Exception {
        List<SealApplicationItem> items = new ArrayList<>();
        for (int i = 1; i <= 36; i++) items.add(item("第" + i + "份工程资料", i));
        String longName = "地下室工程防水施工技术交底及质量验收记录".repeat(10);
        items.set(10, item(longName, 2));
        byte[] bytes = renderer.render(List.of(application(1L, "申请人", "审批人")), Map.of(1L, items));
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            var table = document.getTables().get(0);
            assertEquals(37, table.getNumberOfRows());
            assertEquals(longName, table.getRow(11).getCell(2).getText());
            assertEquals("36", table.getRow(36).getCell(0).getText());
            assertTrue(table.getRows().stream().allMatch(row -> row.isCantSplitRow()));
            assertEquals("黑体", table.getRow(1).getCell(2).getParagraphs().get(0).getRuns().get(0)
                    .getFontFamily(org.apache.poi.xwpf.usermodel.XWPFRun.FontCharRange.eastAsia));
        }
        artifact("seal-ledger-multipage.docx", bytes);
    }

    private SealApplication application(long id, String applicant, String approver) {
        SealApplication application = new SealApplication();
        application.setId(id);
        application.setApprovalTime(LocalDateTime.of(2026, 9, 11, 12, 30));
        application.setDepartmentName("测试项目部");
        application.setApplicantName(applicant);
        application.setApproverName(approver);
        return application;
    }

    private SealApplicationItem item(String name, Integer copies) {
        var item = new SealApplicationItem();
        item.setDocumentName(name);
        item.setCopies(copies);
        return item;
    }

    private Map<String, byte[]> unzip(byte[] data) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(data))) {
            java.util.zip.ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) result.put(entry.getName(), input.readAllBytes());
        }
        return result;
    }

    private void artifact(String name, byte[] bytes) throws Exception {
        Path directory = Path.of(System.getProperty("seal.ledger.qaOutputDir", "target/test-artifacts"));
        Files.createDirectories(directory);
        Files.write(directory.resolve(name), bytes);
    }
}
