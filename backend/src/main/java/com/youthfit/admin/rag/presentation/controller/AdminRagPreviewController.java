package com.youthfit.admin.rag.presentation.controller;

import com.youthfit.admin.rag.application.service.RagPreviewService;
import com.youthfit.admin.rag.presentation.dto.request.RagPreviewRequest;
import com.youthfit.admin.rag.presentation.dto.response.RagPreviewResponse;
import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/rag")
@RequiredArgsConstructor
public class AdminRagPreviewController implements AdminRagPreviewApi {

    private final RagPreviewService service;

    @Override
    @PostMapping("/preview")
    public ResponseEntity<RagPreviewResponse> preview(
            @Valid @RequestBody RagPreviewRequest request,
            Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new YouthFitException(ErrorCode.UNAUTHORIZED);
        }
        long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(RagPreviewResponse.from(
                service.preview(request.toCommand(userId))));
    }
}
