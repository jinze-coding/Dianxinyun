package com.example.siteplatform.seal.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.seal.entity.SealApplication;
import com.example.siteplatform.seal.vo.SealApplicationItemVO;
import com.example.siteplatform.seal.vo.SealApplicationVO;
import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SealPdfService {
    private static final String TEMPLATE = "templates/seal-application-form.xhtml";
    private static final String REGULAR_FONT = "fonts/ttf/NotoSansSC/NotoSansSC-Regular.ttf";
    private static final String BOLD_FONT = "fonts/ttf/NotoSansSC/NotoSansSC-Bold.ttf";
    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\$\\{([A-Z_]+)}");
    private static final Pattern STYLE_ELEMENT = Pattern.compile("(?is)<style[^>]*>.*?</style>");
    private static final Pattern XHTML_ELEMENT = Pattern.compile("(?is)<[^>]+>");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SealApplicationService applicationService;

    public SealPdfService(SealApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Transactional
    public byte[] generate(Long applicationId, SysUser currentUser, HttpServletRequest request) {
        SealApplication application = applicationService.requireApplication(applicationId);
        applicationService.requireReadable(application, currentUser);
        if (!SealApplicationService.APPROVED.equals(application.getStatus())) {
            throw BusinessException.of(409, "仅审批通过的用印申请可以生成正式申请单 PDF");
        }
        byte[] pdf = render(applicationService.detail(applicationId, currentUser));
        applicationService.recordExternalAction(application, "EXPORT_PDF", currentUser, null,
                "导出用印申请单 PDF", request);
        return pdf;
    }

    /** Package-visible for deterministic PDF rendering and extraction tests. */
    byte[] render(SealApplicationVO application) {
        String html = renderTemplate(application);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.useFont(font(REGULAR_FONT), "Noto Sans SC", 400,
                    BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.useFont(font(BOLD_FONT), "Noto Sans SC", 700,
                    BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.withProducer("智慧营造用印申请");
            builder.toStream(output);
            builder.run();
            return installExactToUnicodeMaps(output.toByteArray(), visibleText(html));
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException("用印申请单 PDF 生成失败");
        }
    }

    /**
     * Noto Sans SC intentionally aliases some unified ideographs to Kangxi-radical glyphs. PDFBox
     * otherwise chooses the first Unicode value for an aliased glyph when it writes ToUnicode,
     * which makes copied/extracted text differ from the application even though it looks correct.
     * Replace that generated map with one derived from the exact text present in this document.
     */
    private byte[] installExactToUnicodeMaps(byte[] pdf, String visibleText) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf);
             TrueTypeFont regular = parseFont(REGULAR_FONT);
             TrueTypeFont bold = parseFont(BOLD_FONT)) {
            Map<Integer, String> regularMap = toUnicodeByGlyph(regular, visibleText);
            Map<Integer, String> boldMap = toUnicodeByGlyph(bold, visibleText);
            Set<COSBase> updatedFonts = Collections.newSetFromMap(new IdentityHashMap<>());
            for (var page : document.getPages()) {
                installExactToUnicodeMaps(document, page.getResources(), regularMap, boldMap, updatedFonts);
            }
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                document.save(output);
                return output.toByteArray();
            }
        }
    }

    private void installExactToUnicodeMaps(PDDocument document,
                                           PDResources resources,
                                           Map<Integer, String> regularMap,
                                           Map<Integer, String> boldMap,
                                           Set<COSBase> updatedFonts) throws IOException {
        if (resources == null) return;
        for (COSName name : resources.getFontNames()) {
            PDFont font = resources.getFont(name);
            if (!(font instanceof PDType0Font)) continue;
            COSDictionary dictionary = font.getCOSObject();
            if (!updatedFonts.add(dictionary)) continue;
            String baseFont = dictionary.getNameAsString(COSName.BASE_FONT, "");
            Map<Integer, String> mapping;
            if (baseFont.contains("NotoSansSC-Bold")) {
                mapping = boldMap;
            } else if (baseFont.contains("NotoSansSC-Regular")) {
                mapping = regularMap;
            } else {
                continue;
            }
            PDStream stream = new PDStream(document,
                    new ByteArrayInputStream(toUnicodeCMap(mapping)), COSName.FLATE_DECODE);
            dictionary.setItem(COSName.TO_UNICODE, stream);
        }
    }

    private TrueTypeFont parseFont(String path) throws IOException {
        try (InputStream input = requiredResource(path)) {
            return new TTFParser().parse(new RandomAccessReadBuffer(input));
        }
    }

    private Map<Integer, String> toUnicodeByGlyph(TrueTypeFont font, String text) throws IOException {
        CmapLookup cmap = font.getUnicodeCmapLookup();
        Map<Integer, String> mappings = new LinkedHashMap<>();
        text.codePoints().forEach(codePoint -> {
            int glyphId = cmap.getGlyphId(codePoint);
            if (glyphId == 0) return;
            String unicode = new String(Character.toChars(codePoint));
            String previous = mappings.putIfAbsent(glyphId, unicode);
            if (previous != null && !previous.equals(unicode)) {
                throw new IllegalStateException("PDF 文本包含无法无损区分的共享字形: "
                        + previous + " / " + unicode);
            }
        });
        return mappings;
    }

    private byte[] toUnicodeCMap(Map<Integer, String> mapping) {
        StringBuilder cmap = new StringBuilder(4096);
        cmap.append("/CIDInit /ProcSet findresource begin\n")
                .append("12 dict begin\nbegincmap\n")
                .append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n")
                .append("/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n")
                .append("1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n");
        List<Map.Entry<Integer, String>> entries = new ArrayList<>(mapping.entrySet());
        entries.sort(Comparator.comparingInt(Map.Entry::getKey));
        for (int offset = 0; offset < entries.size(); offset += 100) {
            int count = Math.min(100, entries.size() - offset);
            cmap.append(count).append(" beginbfchar\n");
            for (int index = offset; index < offset + count; index++) {
                Map.Entry<Integer, String> entry = entries.get(index);
                cmap.append('<').append(String.format("%04X", entry.getKey())).append("> <")
                        .append(toHexUtf16(entry.getValue())).append(">\n");
            }
            cmap.append("endbfchar\n");
        }
        cmap.append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n");
        return cmap.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private String toHexUtf16(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_16BE);
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte valueByte : bytes) hex.append(String.format("%02X", valueByte & 0xff));
        return hex.toString();
    }

    private String visibleText(String html) {
        String withoutStyles = STYLE_ELEMENT.matcher(html).replaceAll(" ");
        String withoutElements = XHTML_ELEMENT.matcher(withoutStyles).replaceAll(" ");
        return decodeXmlEntities(withoutElements) + " 第 页 共 0123456789 /";
    }

    private String decodeXmlEntities(String value) {
        return value.replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replace("&gt;", ">")
                .replace("&lt;", "<")
                .replace("&amp;", "&");
    }

    private String renderTemplate(SealApplicationVO application) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("TITLE", escaped("用印申请单-" + value(application.getApplicationNo(), "草稿")));
        values.put("COMPANY_NAME", escaped(value(application.getCompanyName(), "-")));
        values.put("APPLICATION_NO", escaped(value(application.getApplicationNo(), "提交后由系统生成")));
        values.put("DEPARTMENT_NAME", escaped(value(application.getDepartmentName(), "-")));
        values.put("SEAL_NAME", escaped(value(application.getSealName(), "-")));
        values.put("PURPOSE", htmlValue(value(application.getPurpose(), "-")));
        values.put("ITEM_ROWS", itemRows(application.getItems()));
        values.put("APPLICANT_NAME", escaped(value(application.getApplicantName(), "-")));
        values.put("APPLICANT_DEPARTMENT", escaped(value(application.getApplicantDepartmentName(), "-")));
        values.put("APPLICANT_PHONE", escaped(value(application.getApplicantPhone(), "-")));
        values.put("APPLICATION_DATE", escaped(application.getApplicationDate() == null
                ? "-" : application.getApplicationDate().toString()));
        values.put("APPROVAL_OPINION", htmlValue(value(application.getApprovalOpinion(), "尚未审批")));
        values.put("APPROVER_NAME", escaped(value(application.getApproverName(), "-")));
        values.put("APPROVAL_TIME", escaped(application.getApprovalTime() == null
                ? "-" : application.getApprovalTime().format(DATE_TIME)));
        String template = resourceText(TEMPLATE);
        Matcher matcher = TEMPLATE_VARIABLE.matcher(template);
        StringBuffer rendered = new StringBuffer(template.length() + 1024);
        while (matcher.find()) {
            String replacement = values.get(matcher.group(1));
            if (replacement == null) throw new IllegalStateException("未知用印申请模板变量: " + matcher.group(1));
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private String itemRows(List<SealApplicationItemVO> items) {
        if (items == null || items.isEmpty()) {
            return "<tr><td class=\"center\">-</td><td>未填写</td><td class=\"center\">-</td></tr>";
        }
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            SealApplicationItemVO item = items.get(i);
            rows.append("<tr><td class=\"center\">").append(i + 1)
                    .append("</td><td>").append(htmlValue(value(item.getDocumentName(), "-")))
                    .append("</td><td class=\"center\">")
                    .append(item.getCopies() == null ? 1 : item.getCopies())
                    .append("</td></tr>");
        }
        return rows.toString();
    }

    private FSSupplier<InputStream> font(String path) {
        return () -> requiredResource(path);
    }

    private InputStream requiredResource(String path) {
        InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (input == null) throw new IllegalStateException("缺少 PDF 资源: " + path);
        return input;
    }

    private String resourceText(String path) {
        try (InputStream input = requiredResource(path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("读取 PDF 模板失败: " + path, exception);
        }
    }

    private String htmlValue(String value) {
        return escaped(value).replace("\r\n", "<br />").replace("\r", "<br />").replace("\n", "<br />");
    }

    private String escaped(String value) {
        return Objects.toString(value, "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
