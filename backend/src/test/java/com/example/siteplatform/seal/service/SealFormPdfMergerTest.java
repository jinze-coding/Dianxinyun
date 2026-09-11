package com.example.siteplatform.seal.service;

import com.example.siteplatform.seal.vo.SealApplicationVO;
import com.example.siteplatform.seal.vo.SealApplicationItemVO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SealFormPdfMergerTest {
    @Test
    void preservesEverySourcePageChineseFontsAndLocalPaginationWithoutBlankPages() throws Exception {
        Map<Long, byte[]> sources = new LinkedHashMap<>();
        SealPdfService pdf = new SealPdfService(null);
        sources.put(3L, pdf.render(form("合并验证甲", 1)));
        sources.put(2L, pdf.render(form("合并验证乙", 20)));
        sources.put(1L, pdf.render(form("合并验证丙", 1)));
        List<String> expectedPages = new ArrayList<>();
        for (byte[] bytes : sources.values()) try (PDDocument source = Loader.loadPDF(bytes)) {
            for (int p = 1; p <= source.getNumberOfPages(); p++) expectedPages.add(pageText(source, p));
        }
        assertTrue(expectedPages.size() >= 4);
        Path artifact = Path.of("target/test-artifacts/seal-merged-sample.pdf");
        Files.createDirectories(artifact.getParent());
        List<Long> rendered = new ArrayList<>();
        List<Integer> progress = new ArrayList<>();
        int pages = SealFormPdfMerger.merge(artifact, List.of(3L, 2L, 1L), id -> {
            rendered.add(id); return sources.get(id);
        }, (count, pageCount) -> progress.add(count));
        assertEquals(List.of(3L, 2L, 1L), rendered);
        assertEquals(List.of(1, 2, 3), progress);
        assertEquals(expectedPages.size(), pages);
        try (PDDocument merged = Loader.loadPDF(artifact.toFile())) {
            assertEquals(pages, merged.getNumberOfPages());
            for (int p = 1; p <= pages; p++) assertEquals(expectedPages.get(p - 1), pageText(merged, p));
            assertTrue(pageText(merged, 1).contains("合并验证甲"));
            assertTrue(pageText(merged, 2).contains("合并验证乙"));
            assertTrue(pageText(merged, pages).contains("合并验证丙"));
            for (var page : merged.getPages()) for (var fontName : page.getResources().getFontNames())
                assertTrue(page.getResources().getFont(fontName).isEmbedded());
        }
    }

    private String pageText(PDDocument document, int page) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(page); stripper.setEndPage(page);
        return stripper.getText(document);
    }

    private SealApplicationVO form(String name, int itemCount) {
        SealApplicationVO form = new SealApplicationVO();
        form.setApplicationNo(name); form.setStatus("APPROVED");
        form.setCompanyName("智慧营造测试单位"); form.setDepartmentName("合并打印测试项目");
        form.setApplicantName("测试申请人"); form.setSealName("测试项目公章");
        form.setPurpose("申请单完整分页与中文验证");
        form.setApproverName("测试审批人"); form.setApprovalOpinion("同意用印，资料内容已核验。");
        form.setApprovalTime(LocalDateTime.of(2026, 9, 11, 10, 0));
        form.setApplicationDate(LocalDate.of(2026, 9, 11));
        List<SealApplicationItemVO> items = new ArrayList<>();
        for (int i = 1; i <= itemCount; i++) {
            SealApplicationItemVO item = new SealApplicationItemVO();
            item.setDocumentName("第" + i + "项施工组织设计、专项方案及验收记录（中文分页与重复表头验证资料）");
            item.setCopies(7); item.setSortOrder(i); items.add(item);
        }
        form.setItems(items); return form;
    }
}
