package com.example.siteplatform.seal.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.seal.entity.SealApplication;
import com.example.siteplatform.seal.vo.SealApplicationItemVO;
import com.example.siteplatform.seal.vo.SealApplicationVO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SealPdfServiceTest {

    @Test
    void formalPdfRejectsDraftAndOtherUnapprovedStates() {
        SealApplicationService applicationService = mock(SealApplicationService.class);
        SealApplication draft = new SealApplication();
        draft.setId(42L);
        draft.setStatus(SealApplicationService.DRAFT);
        SysUser user = new SysUser();
        user.setId(7L);
        when(applicationService.requireApplication(42L)).thenReturn(draft);

        BusinessException error = assertThrows(BusinessException.class,
                () -> new SealPdfService(applicationService).generate(42L, user, null));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("仅审批通过"));
        verify(applicationService).requireReadable(draft, user);
        verify(applicationService, never()).detail(42L, user);
    }

    @Test
    void bundledChineseFontsCarryTheSilOpenFontLicense() throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        assertTrue(classLoader.getResource("fonts/ttf/NotoSansSC/NotoSansSC-Regular.ttf") != null);
        assertTrue(classLoader.getResource("fonts/ttf/NotoSansSC/NotoSansSC-Bold.ttf") != null);
        try (var licenseInput = classLoader.getResourceAsStream("fonts/ttf/NotoSansSC/OFL.txt")) {
            assertTrue(licenseInput != null);
            String license = new String(licenseInput.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(license.contains("SIL OPEN FONT LICENSE Version 1.1"));
            assertTrue(license.contains("Copyright 2012 Google Inc."));
        }
    }

    @Test
    void normalChineseThatSharesFontGlyphsWithRadicalsExtractsExactly() throws Exception {
        byte[] bytes = new SealPdfService(null).render(application());
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("上海建工智慧营造有限公司"));
            assertTrue(text.contains("用印申请单（项目部级）"));
            assertFalse(text.codePoints().anyMatch(codePoint -> codePoint >= 0x2E80 && codePoint <= 0x2FDF));
        }
    }

    @Test
    void xhtmlTemplateProducesSearchableMultipageChinesePdf() throws Exception {
        SealApplicationVO application = application();
        byte[] bytes = new SealPdfService(null).render(application);
        Path artifact = Path.of("target", "test-artifacts", "seal-form-sample.pdf");
        Files.createDirectories(artifact.getParent());
        Files.write(artifact, bytes);

        assertArrayEquals("%PDF".getBytes(StandardCharsets.US_ASCII), java.util.Arrays.copyOf(bytes, 4));
        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertTrue(document.getNumberOfPages() >= 2, "20条长文件名应自动分页");
            String text = new PDFTextStripper().getText(document);
            Files.writeString(Path.of("target", "test-artifacts", "seal-form-sample.txt"), text);
            assertTrue(text.contains("上海建工智慧营造有限公司"));
            assertTrue(text.contains("用印申请单（项目部级）"));
            assertTrue(text.contains("系统审批"));
            assertTrue(text.contains("项目经理意见"));
            assertTrue(text.contains("研发 & <归档>"));
            assertTrue(text.contains("用印文件明细"));
            String compactText = text.replaceAll("\\s+", "");
            assertTrue(compactText.contains("第20项施工组织设计、专项方案及验收记录（中文分页与重复表头验证资料）"));
            assertFalse(text.codePoints().anyMatch(codePoint -> codePoint >= 0x2E80 && codePoint <= 0x2FDF),
                    "抽取文本不得用部首兼容字符替代原中文");
            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                PDFTextStripper pageStripper = new PDFTextStripper();
                pageStripper.setStartPage(pageNumber);
                pageStripper.setEndPage(pageNumber);
                String pageText = pageStripper.getText(document);
                assertTrue(pageText.contains("用印文件明细"), "每个明细续页都应重复表头");
                assertTrue(pageText.contains("用印文件名称"), "每个明细续页都应重复列标题");
            }
            document.getPages().forEach(page -> page.getResources().getFontNames().forEach(fontName -> {
                try {
                    assertTrue(page.getResources().getFont(fontName).getFontDescriptor().getFontFile2() != null,
                            "申请单字体必须嵌入 PDF");
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }));
        }

    }

    private SealApplicationVO application() {
        SealApplicationVO application = new SealApplicationVO();
        application.setApplicationNo("YYSQ-20260808-00000042");
        application.setCompanyName("上海建工智慧营造有限公司");
        application.setDepartmentName("智慧营造演示项目");
        application.setSealName("智慧营造演示项目项目章");
        application.setPurpose("研发 & <归档>\n用于验证服务端 XHTML 模板生成中文 PDF");
        application.setApplicantName("张三");
        application.setApplicantDepartmentName("智慧营造演示项目");
        application.setApplicantPhone("19900000000");
        application.setApplicationDate(LocalDate.of(2026, 8, 8));
        application.setApprovalOpinion("同意用印，资料内容已核验。");
        application.setApproverName("项目经理李四");
        application.setApprovalTime(LocalDateTime.of(2026, 8, 8, 14, 30));
        List<SealApplicationItemVO> items = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            SealApplicationItemVO item = new SealApplicationItemVO();
            item.setId((long) i);
            item.setSortOrder(i);
            item.setCopies(i % 3 + 1);
            item.setDocumentName("第" + i + "项施工组织设计、专项方案及验收记录（中文分页与重复表头验证资料）");
            items.add(item);
        }
        application.setItems(items);
        return application;
    }
}
