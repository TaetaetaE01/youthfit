package com.youthfit.policy.application.dto.result;

import java.io.InputStream;

/**
 * 첨부 redirect 결과 sealed type.
 * Controller 는 이 결과를 ResponseEntity 로 변환만 수행한다.
 */
public sealed interface AttachmentRedirectResult {

    /** S3 등 presigned URL 로 302 redirect. */
    record PresignRedirect(String url) implements AttachmentRedirectResult {}

    /** 외부 원본 URL 로 302 redirect (S3 캐시 없거나 presign 실패 후 외부만 가능한 경우). */
    record ExternalRedirect(String url) implements AttachmentRedirectResult {}

    /** Local 등 storage 가 직접 stream 으로 응답. */
    record StreamResponse(
            InputStream stream,
            String mediaType,
            String filename) implements AttachmentRedirectResult {}
}
