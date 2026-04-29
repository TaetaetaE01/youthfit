package com.youthfit.policy.presentation.controller;

import com.youthfit.policy.application.dto.result.AttachmentRedirectResult;
import com.youthfit.policy.application.service.RedirectAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/policies/attachments")
@RequiredArgsConstructor
public class PolicyAttachmentController implements PolicyAttachmentApi {

    private final RedirectAttachmentService redirectAttachmentService;

    @Override
    @GetMapping("/{attachmentId}/file")
    public ResponseEntity<?> redirectFile(@PathVariable Long attachmentId) {
        AttachmentRedirectResult result = redirectAttachmentService.resolve(attachmentId);

        return switch (result) {
            case AttachmentRedirectResult.PresignRedirect r ->
                    ResponseEntity.status(302).location(URI.create(r.url())).build();
            case AttachmentRedirectResult.ExternalRedirect r ->
                    ResponseEntity.status(302).location(URI.create(r.url())).build();
            case AttachmentRedirectResult.StreamResponse r -> {
                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + r.filename() + "\"");
                yield ResponseEntity.ok()
                        .headers(headers)
                        .contentType(MediaType.parseMediaType(r.mediaType()))
                        .body(new InputStreamResource(r.stream()));
            }
        };
    }
}
