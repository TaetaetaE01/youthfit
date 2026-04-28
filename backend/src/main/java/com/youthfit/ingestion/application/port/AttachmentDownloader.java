package com.youthfit.ingestion.application.port;

import java.io.InputStream;

public interface AttachmentDownloader {
    DownloadedFile download(String url, long maxBytes);

    record DownloadedFile(InputStream stream, long sizeBytes, String detectedMediaType) implements AutoCloseable {
        @Override public void close() throws Exception { stream.close(); }
    }

    class DownloadException extends RuntimeException {
        public DownloadException(String msg, Throwable cause) { super(msg, cause); }
    }

    class OversizedException extends RuntimeException {
        public OversizedException(String msg) { super(msg); }
    }
}
