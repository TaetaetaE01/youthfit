package com.youthfit.ingestion.application.port;

public record StorageReference(String key, long sizeBytes, String sha256Hex) {
}
