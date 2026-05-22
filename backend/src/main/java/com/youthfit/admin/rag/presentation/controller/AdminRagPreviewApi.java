package com.youthfit.admin.rag.presentation.controller;

import com.youthfit.admin.rag.presentation.dto.request.RagPreviewRequest;
import com.youthfit.admin.rag.presentation.dto.response.RagPreviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;

@Tag(name = "Admin RAG Preview", description = "어드민 — RAG 파라미터 미리보기 (baseline vs candidate 비교)")
public interface AdminRagPreviewApi {

    @Operation(summary = "RAG preview — baseline 과 candidate 검색 결과 비교")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "미리보기 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "정책 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "레이트 리밋 초과 (Retry-After: 60)")
    })
    ResponseEntity<RagPreviewResponse> preview(
            @Valid @RequestBody RagPreviewRequest request,
            Authentication authentication);
}
