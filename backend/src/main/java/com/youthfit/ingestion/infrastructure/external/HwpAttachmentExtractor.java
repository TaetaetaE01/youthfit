package com.youthfit.ingestion.infrastructure.external;

import com.youthfit.ingestion.application.port.AttachmentExtractor;
import com.youthfit.ingestion.application.port.ExtractionResult;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Set;

@Component
@Order(10)
public class HwpAttachmentExtractor implements AttachmentExtractor {

    private static final Set<String> SUPPORTED = Set.of(
            "application/x-hwp",
            "application/haansofthwp",
            "application/vnd.hancom.hwp"
    );

    @Override
    public boolean supports(String mediaType) {
        if (mediaType == null) return false;
        return SUPPORTED.contains(mediaType.toLowerCase());
    }

    @Override
    public ExtractionResult extract(InputStream stream, long sizeBytes) {
        try {
            HWPFile hwp = HWPReader.fromInputStream(stream);
            String text = TextExtractor.extract(hwp, TextExtractMethod.AppendControlTextAfterParagraphText);
            return ExtractionResult.success(text == null ? "" : text.trim());
        } catch (Exception e) {
            return ExtractionResult.failed("hwp extract: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
