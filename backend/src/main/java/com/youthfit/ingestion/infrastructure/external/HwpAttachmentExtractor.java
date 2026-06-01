package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.AttachmentExtractor;
import com.youthfit.ingestion.application.port.ExtractionResult;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 한글 문서(hwp/hwpx) 첨부 텍스트 추출기.
 *
 * <p>mediaType 만으로는 구형 hwp(OLE2)와 신형 hwpx(zip/XML)를 구분할 수 없다
 * (크롤러가 .hwpx 를 application/x-hwp 로 보내기도 한다). 따라서 실제 바이트의
 * 시그니처로 분기한다: zip 매직(PK)이면 hwpx, 아니면 hwplib 으로 구형 hwp 처리.
 */
@Component
@Order(10)
public class HwpAttachmentExtractor implements AttachmentExtractor {

    private static final Set<String> SUPPORTED = Set.of(
            "application/x-hwp",
            "application/haansofthwp",
            "application/vnd.hancom.hwp"
    );

    private static final Pattern SECTION_ENTRY = Pattern.compile("Contents/section\\d+\\.xml");

    @Override
    public boolean supports(String mediaType) {
        if (mediaType == null) return false;
        return SUPPORTED.contains(mediaType.toLowerCase());
    }

    @Override
    public ExtractionResult extract(InputStream stream, long sizeBytes) {
        try {
            byte[] bytes = stream.readAllBytes();
            if (isZip(bytes)) {
                return extractHwpx(bytes);
            }
            return extractHwp(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return ExtractionResult.failed("hwp extract: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static boolean isZip(byte[] b) {
        return b.length >= 4 && b[0] == 0x50 && b[1] == 0x4B
                && (b[2] == 0x03 || b[2] == 0x05 || b[2] == 0x07);
    }

    private ExtractionResult extractHwp(InputStream stream) throws Exception {
        HWPFile hwp = HWPReader.fromInputStream(stream);
        String text = TextExtractor.extract(hwp, TextExtractMethod.AppendControlTextAfterParagraphText);
        return ExtractionResult.success(text == null ? "" : text.trim());
    }

    private ExtractionResult extractHwpx(byte[] bytes) throws Exception {
        // section 파일은 0,1,2... 순서가 본문 순서이므로 이름순으로 정렬해 합친다.
        TreeMap<String, String> sections = new TreeMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (SECTION_ENTRY.matcher(entry.getName()).matches()) {
                    sections.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String xml : sections.values()) {
            appendParagraphText(xml, sb);
        }
        return ExtractionResult.success(sb.toString().trim());
    }

    /** hwpx section XML 에서 문단(hp:p) 단위로 텍스트런(hp:t)을 이어붙여 한 줄씩 추가한다. */
    private void appendParagraphText(String xml, StringBuilder sb) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // 크롤링 소스 방어: 외부 엔티티/DTD 차단 (XXE)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);

        Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

        NodeList paragraphs = doc.getElementsByTagNameNS("*", "p");
        if (paragraphs.getLength() == 0) {
            // 문단 구조가 없으면 전체 텍스트런을 fallback 으로 수집
            appendTextRuns(doc.getDocumentElement(), sb, true);
            return;
        }
        for (int i = 0; i < paragraphs.getLength(); i++) {
            StringBuilder line = new StringBuilder();
            appendTextRuns((Element) paragraphs.item(i), line, false);
            String trimmed = line.toString().strip();
            if (!trimmed.isEmpty()) {
                sb.append(trimmed).append('\n');
            }
        }
    }

    private void appendTextRuns(Element scope, StringBuilder out, boolean newlinePerRun) {
        NodeList texts = scope.getElementsByTagNameNS("*", "t");
        for (int i = 0; i < texts.getLength(); i++) {
            Node t = texts.item(i);
            out.append(t.getTextContent());
            if (newlinePerRun) out.append('\n');
        }
    }
}
