package com.youthfit.policy.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "정책 첨부", description = "정책 첨부 파일 redirect / stream API")
public interface PolicyAttachmentApi {

    @Operation(summary = "첨부 파일 redirect / stream",
            description = "S3 캐시가 있고 presign 가능하면 presigned URL 로 302, "
                    + "Local storage 면 직접 stream(200), storageKey 가 없고 외부 URL 만 있으면 외부 원본으로 302. "
                    + "fragment(#page=N) 는 클라이언트 측에서 final URL 에 자동 보존된다 (RFC 7231).")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "presigned 또는 외부 URL 로 redirect"),
            @ApiResponse(responseCode = "200", description = "Local storage stream 응답"),
            @ApiResponse(responseCode = "404", description = "첨부 없음 또는 storage/외부 URL 모두 부재 (ATTACHMENT_NOT_FOUND)")
    })
    @SecurityRequirements
    ResponseEntity<?> redirectFile(
            @Parameter(description = "첨부 ID") Long attachmentId);
}
