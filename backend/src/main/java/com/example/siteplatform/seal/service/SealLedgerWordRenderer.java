package com.example.siteplatform.seal.service;

import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.seal.entity.SealApplication;
import com.example.siteplatform.seal.entity.SealApplicationItem;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Fills the retained attachment-8 DOCX; every other package part stays byte-for-byte intact. */
@Component
public class SealLedgerWordRenderer {
    public static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String DOCUMENT_PART = "word/document.xml";
    private static final String ROWS_MARKER = "seal-ledger-rows";
    private final List<TemplatePart> parts;

    public SealLedgerWordRenderer() {
        try (InputStream input = new ClassPathResource("templates/seal-ledger.docx").getInputStream();
             ZipInputStream zip = new ZipInputStream(input)) {
            List<TemplatePart> loaded = new ArrayList<>();
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                loaded.add(new TemplatePart(entry.getName(), zip.readAllBytes(), entry.getTime()));
            }
            if (loaded.stream().noneMatch(part -> DOCUMENT_PART.equals(part.name()))) {
                throw new IOException("Missing document.xml");
            }
            parts = List.copyOf(loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("用印台账 Word 模板读取失败", exception);
        }
    }

    public byte[] render(List<SealApplication> applications,
                         Map<Long, List<SealApplicationItem>> itemsByApplication) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            for (TemplatePart part : parts) {
                ZipEntry entry = new ZipEntry(part.name());
                if (part.time() >= 0) entry.setTime(part.time());
                zip.putNextEntry(entry);
                if (DOCUMENT_PART.equals(part.name())) {
                    writeDocument(zip, part.content(), applications, itemsByApplication);
                } else {
                    zip.write(part.content());
                }
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        } catch (IOException | ParserConfigurationException | SAXException | TransformerException exception) {
            throw new BusinessException("用印台账 Word 生成失败");
        }
    }

    private void writeDocument(ZipOutputStream output, byte[] source, List<SealApplication> applications,
                               Map<Long, List<SealApplicationItem>> itemsByApplication)
            throws ParserConfigurationException, IOException, SAXException, TransformerException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(source));
        Element table = (Element) document.getElementsByTagNameNS(WORD_NS, "tbl").item(0);
        List<Element> rows = children(table, "tr");
        if (rows.size() < 2 || children(rows.get(1), "tc").size() != 7) {
            throw new IOException("Unexpected ledger template table");
        }
        Element pattern = (Element) rows.get(1).cloneNode(true);
        Element headerProperties = properties(rows.get(0), "trPr");
        ensure(headerProperties, "tblHeader").setAttributeNS(WORD_NS, "w:val", "true");
        ensure(headerProperties, "cantSplit").setAttributeNS(WORD_NS, "w:val", "true");
        ensure(properties(table, "tblPr"), "tblLayout").setAttributeNS(WORD_NS, "w:type", "fixed");
        for (int i = 1; i < rows.size(); i++) table.removeChild(rows.get(i));
        table.appendChild(document.createProcessingInstruction(ROWS_MARKER, ""));

        TransformerFactory transformers = TransformerFactory.newInstance();
        transformers.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = transformers.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        ByteArrayOutputStream frameOutput = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(document), new StreamResult(frameOutput));
        String frame = frameOutput.toString(StandardCharsets.UTF_8);
        int markerStart = frame.indexOf("<?" + ROWS_MARKER);
        int markerEnd = frame.indexOf("?>", markerStart) + 2;
        if (markerStart < 0 || markerEnd < 2) throw new IOException("Missing ledger row slot");
        output.write(frame.substring(0, markerStart).getBytes(StandardCharsets.UTF_8));
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        // Stream one cloned row at a time, avoiding a second document-sized DOM for large ledgers.
        int sequence = 1;
        for (SealApplication application : applications) {
            List<SealApplicationItem> items = itemsByApplication.getOrDefault(application.getId(), List.of());
            // Do not silently lose a historical approved application with missing detail data.
            if (items.isEmpty()) items = List.of(new SealApplicationItem());
            for (SealApplicationItem item : items) {
                Element row = (Element) pattern.cloneNode(true);
                Element rowProperties = properties(row, "trPr");
                ensure(rowProperties, "cantSplit").setAttributeNS(WORD_NS, "w:val", "true");
                for (Element height : children(rowProperties, "trHeight")) {
                    height.setAttributeNS(WORD_NS, "w:hRule", "atLeast");
                }
                String[] values = {String.valueOf(sequence++),
                        // Break at the year boundary so ISO dates fit the template's narrow column at its original font size.
                        application.getApprovalTime() == null ? ""
                                : application.getApprovalTime().toLocalDate().toString().replaceFirst("-", "-\n"),
                        item.getDocumentName(), item.getCopies() == null ? "" : item.getCopies().toString(),
                        application.getDepartmentName(), application.getApplicantName(), application.getApproverName()};
                List<Element> cells = children(row, "tc");
                for (int i = 0; i < values.length; i++) fillCell(cells.get(i), values[i]);
                transformer.transform(new DOMSource(row), new StreamResult(output));
            }
        }
        output.write(frame.substring(markerEnd).getBytes(StandardCharsets.UTF_8));
    }

    private static void fillCell(Element cell, String value) {
        Document document = cell.getOwnerDocument();
        Element paragraph = children(cell, "p").get(0);
        Node font = paragraph.getElementsByTagNameNS(WORD_NS, "rPr").item(0);
        Node runProperties = font == null ? null : font.cloneNode(true);
        for (Node node = paragraph.getFirstChild(); node != null;) {
            Node next = node.getNextSibling();
            if (!(node instanceof Element element) || !"pPr".equals(element.getLocalName())) {
                paragraph.removeChild(node);
            }
            node = next;
        }
        Element run = document.createElementNS(WORD_NS, "w:r");
        if (runProperties != null) run.appendChild(runProperties);
        String text = value == null ? "" : validXmlText(value);
        String[] lines = text.split("\\r\\n|\\r|\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) run.appendChild(document.createElementNS(WORD_NS, "w:br"));
            Element content = document.createElementNS(WORD_NS, "w:t");
            content.setAttributeNS(XMLConstants.XML_NS_URI, "xml:space", "preserve");
            content.setTextContent(lines[i]);
            run.appendChild(content);
        }
        paragraph.appendChild(run);
    }

    private static String validXmlText(String value) {
        StringBuilder result = new StringBuilder(value.length());
        value.codePoints().filter(code -> code == 9 || code == 10 || code == 13
                        || (code >= 0x20 && code <= 0xD7FF) || (code >= 0xE000 && code <= 0xFFFD)
                        || (code >= 0x10000 && code <= 0x10FFFF))
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    private static Element properties(Element parent, String name) {
        List<Element> existing = children(parent, name);
        if (!existing.isEmpty()) return existing.get(0);
        Element property = parent.getOwnerDocument().createElementNS(WORD_NS, "w:" + name);
        parent.insertBefore(property, parent.getFirstChild());
        return property;
    }

    private static Element ensure(Element parent, String name) {
        List<Element> existing = children(parent, name);
        if (!existing.isEmpty()) return existing.get(0);
        Element element = parent.getOwnerDocument().createElementNS(WORD_NS, "w:" + name);
        parent.appendChild(element);
        return element;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        if (parent == null) return result;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && WORD_NS.equals(element.getNamespaceURI())
                    && name.equals(element.getLocalName())) result.add(element);
        }
        return result;
    }

    private record TemplatePart(String name, byte[] content, long time) { }
}
