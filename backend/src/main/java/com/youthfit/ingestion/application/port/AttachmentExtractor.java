package com.youthfit.ingestion.application.port;

import java.io.InputStream;

public interface AttachmentExtractor {
    boolean supports(String mediaType);
    ExtractionResult extract(InputStream stream, long sizeBytes);
}
