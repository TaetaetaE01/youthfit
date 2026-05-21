package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.presentation.dto.request.CreateJobRequest;
import com.youthfit.admin.presentation.dto.request.ReferenceSiteRequest;
import com.youthfit.admin.presentation.dto.response.EnrichmentCandidateResponse;
import com.youthfit.admin.presentation.dto.response.EnrichmentCandidateSummaryResponse;
import com.youthfit.admin.presentation.dto.response.EnrichmentJobResponse;
import com.youthfit.admin.presentation.dto.response.JobAcceptedResponse;
import com.youthfit.admin.presentation.dto.response.PolicyEnrichmentDetailResponse;
import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.EnrichmentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;

@Tag(name = "Admin Enrichment Review", description = "어드민 — Enrichment 검토 콘솔 (후보/상세/잡)")
public interface AdminEnrichmentApi {

    @Operation(summary = "Enrichment 검토 후보 목록")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "권한 부족")
    })
    Page<EnrichmentCandidateResponse> getCandidates(
            @Parameter(description = "needsReview 만 필터, 기본 true")
            @RequestParam(defaultValue = "true") Boolean needsReview,
            @Parameter(description = "Enrichment 상태 필터")
            @RequestParam(required = false) Set<EnrichmentStatus> statuses,
            @Parameter(description = "DetailLevel 필터")
            @RequestParam(required = false) Set<DetailLevel> detailLevels,
            @Parameter(description = "검색 키워드 (title/organization)")
            @RequestParam(required = false) String q,
            Pageable pageable);

    @Operation(summary = "Enrichment 검토 후보 현황 요약")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "권한 부족")
    })
    EnrichmentCandidateSummaryResponse getCandidateSummary();

    @Operation(summary = "정책 enrichment 상세 + 최근 잡 이력")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "권한 부족"),
            @ApiResponse(responseCode = "404", description = "정책 없음")
    })
    PolicyEnrichmentDetailResponse getPolicyDetail(
            @Parameter(description = "정책 id") @PathVariable Long policyId);

    @Operation(summary = "정책 참조 사이트(URL) 갱신")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "갱신 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 오류"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "권한 부족"),
            @ApiResponse(responseCode = "404", description = "정책 없음")
    })
    ResponseEntity<Void> updateReferenceSites(
            @Parameter(description = "정책 id") @PathVariable Long policyId,
            @RequestBody @Valid List<ReferenceSiteRequest> body);

    @Operation(summary = "강제 enrichment 잡 생성")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "잡이 큐에 들어감"),
            @ApiResponse(responseCode = "400", description = "URL 누락 등 입력값 오류"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "권한 부족"),
            @ApiResponse(responseCode = "404", description = "정책 없음"),
            @ApiResponse(responseCode = "409", description = "이미 진행 중인 잡 존재"),
            @ApiResponse(responseCode = "429", description = "레이트 리밋 초과")
    })
    ResponseEntity<JobAcceptedResponse> createJob(
            @Parameter(description = "정책 id") @PathVariable Long policyId,
            @RequestBody(required = false) CreateJobRequest body,
            Authentication authentication);

    @Operation(summary = "EnrichmentJob 단건 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "권한 부족"),
            @ApiResponse(responseCode = "404", description = "잡 없음")
    })
    EnrichmentJobResponse getJob(
            @Parameter(description = "잡 id") @PathVariable Long jobId);
}
