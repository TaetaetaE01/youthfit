package com.youthfit.ingestion.application.port;

import java.io.InputStream;

public interface AttachmentStorage {
    /**
     * 스트림을 저장하고 SHA-256 / 크기를 함께 계산하여 StorageReference 반환.
     * @param key  저장 식별자. 호출자 책임으로 unique 보장 (예: attachments/{yyyy}/{mm}/{uuid}.{ext})
     */
    StorageReference put(InputStream content, String key, String mediaType);

    InputStream get(String key);

    boolean exists(String key);
}
