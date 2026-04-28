package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.AttachmentExtractor;
import com.youthfit.ingestion.application.port.ExtractionResult;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
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
            BodyContentHandler handler = new BodyContentHandler(-1);
            new AutoDetectParser().parse(stream, handler, new Metadata(), new ParseContext());
            String text = handler.toString().trim();
            return ExtractionResult.success(text);
        } catch (IOException | SAXException | TikaException e) {
            return ExtractionResult.failed("tika extract: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
