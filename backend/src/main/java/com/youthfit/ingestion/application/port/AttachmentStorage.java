package com.youthfit.ingestion.application.port;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

public interface AttachmentStorage {
    /**
     * 스트림을 저장하고 SHA-256 / 크기를 함께 계산하여 StorageReference 반환.
     * @param key  저장 식별자. 호출자 책임으로 unique 보장 (예: attachments/{yyyy}/{mm}/{uuid}.{ext})
     */
    StorageReference put(InputStream content, String key, String mediaType);

    InputStream get(String key);

    boolean exists(String key);

    /**
     * 외부 노출 가능한 presigned URL 발급. S3 등 cloud storage 만 override.
     * Local 등 default 는 Optional.empty() — controller 가 stream 응답으로 fallback.
     */
    default Optional<String> presign(String key, Duration ttl) {
        return Optional.empty();
    }
}
