package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.AttachmentExtractor;
import com.youthfit.ingestion.application.port.ExtractionResult;
import com.youthfit.ingestion.domain.model.PageText;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

@Component
@Order(20)
public class TikaAttachmentExtractor implements AttachmentExtractor {

    private static final Set<String> SUPPORTED = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/html",
            "text/plain"
    );

    @Override
    public boolean supports(String mediaType) {
        if (mediaType == null) return false;
        return SUPPORTED.contains(mediaType.toLowerCase());
    }

    @Override
    public ExtractionResult extract(InputStream stream, long sizeBytes) {
        try {
            PageAwareContentHandler handler = new PageAwareContentHandler();
            new AutoDetectParser().parse(stream, handler, new Metadata(), new ParseContext());
            String sentineled = toSentineledText(handler.getPages());
            return ExtractionResult.success(sentineled);
        } catch (IOException | SAXException | TikaException e) {
            return ExtractionResult.failed("tika extract: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 페이지 단위 텍스트를 form-feed + page tag sentinel 로 직렬화한다.
     * <p>형식: {@code \f<page=N>\n{text}\n\f<page=N+1>\n{text}...}
     * <p>페이지 메타가 없는 입력(plain text / HWP 등)은 {@code <page=null>} 로 박혀
     * 후속 단계에서 첨부 단위 fallback 으로 자연스럽게 흐른다.
     */
    private String toSentineledText(List<PageText> pages) {
        StringBuilder sb = new StringBuilder();
        for (PageText p : pages) {
            sb.append('\f');
            sb.append("<page=").append(p.page() == null ? "null" : p.page()).append(">\n");
            sb.append(p.text()).append('\n');
        }
        return sb.toString();
    }
}
