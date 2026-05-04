package com.youthfit.policy.presentation.controller;

import com.youthfit.policy.application.dto.result.AttachmentRedirectResult;
import com.youthfit.policy.application.service.RedirectAttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

@RestController
@RequestMapping("/api/policies/attachments")
@RequiredArgsConstructor
@Slf4j
public class PolicyAttachmentController implements PolicyAttachmentApi {

    private static final Duration CACHE_MAX_AGE = Duration.ofDays(1);

    private final RedirectAttachmentService redirectAttachmentService;

    @Override
    @GetMapping("/{attachmentId}/file")
    public ResponseEntity<?> redirectFile(
            @PathVariable Long attachmentId,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        AttachmentRedirectResult result = redirectAttachmentService.resolve(attachmentId);

        return switch (result) {
            case AttachmentRedirectResult.PresignRedirect r ->
                    ResponseEntity.status(302).location(URI.create(r.url())).build();
            case AttachmentRedirectResult.ExternalRedirect r ->
                    ResponseEntity.status(302).location(URI.create(r.url())).build();
            case AttachmentRedirectResult.StreamResponse r -> streamWithCache(r, ifNoneMatch);
        };
    }

    private ResponseEntity<?> streamWithCache(AttachmentRedirectResult.StreamResponse r,
                                              String ifNoneMatch) {
        String etag = r.fileHash() == null ? null : "\"" + r.fileHash() + "\"";
        CacheControl cacheControl = CacheControl.maxAge(CACHE_MAX_AGE).cachePrivate();

        if (etag != null && etag.equals(ifNoneMatch)) {
            closeQuietly(r.stream());
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(cacheControl)
                    .build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + r.filename() + "\"");

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .headers(headers)
                .cacheControl(cacheControl)
                .contentType(MediaType.parseMediaType(r.mediaType()));
        if (etag != null) {
            builder.eTag(etag);
        }
        return builder.body(new InputStreamResource(r.stream()));
    }

    private void closeQuietly(java.io.InputStream stream) {
        try {
            stream.close();
        } catch (IOException e) {
            log.debug("attachment-redirect 304 응답 후 stream close 실패", e);
        }
    }
}
