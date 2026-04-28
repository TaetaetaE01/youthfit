package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.port.AttachmentExtractor;
import com.youthfit.ingestion.application.port.ExtractionResult;
import com.youthfit.policy.domain.model.SkipReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ExtractionDispatcher {

    private final List<AttachmentExtractor> extractors;

    public ExtractionResult dispatch(InputStream stream, long sizeBytes, String mediaType) {
        return extractors.stream()
                .filter(e -> e.supports(mediaType))
                .findFirst()
                .map(e -> e.extract(stream, sizeBytes))
                .orElse(ExtractionResult.skipped(SkipReason.UNSUPPORTED_MIME));
    }
}
