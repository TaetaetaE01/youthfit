package com.youthfit.admin.presentation.api;

import com.youthfit.admin.presentation.dto.request.ReprocessPolicyRequest;
import com.youthfit.admin.presentation.dto.response.PolicyProcessingDetailResponse;
import com.youthfit.admin.presentation.dto.response.PolicyProcessingListResponse;
import com.youthfit.admin.presentation.dto.response.PolicyProcessingStatsResponse;
import com.youthfit.admin.presentation.dto.response.ReprocessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 어드민 — 정책 처리 현황 대시보드 API.
 *
 * <p>5단계(INGESTION/ENRICHMENT/GUIDE/RULE/RAG_INDEXING) 진행 상황 +
 * 첨부 임베딩 + 참고 사이트 fetch 결과를 한 화면에 노출한다.</p>
 */
@Tag(
        name = "어드민 - 정책 처리 현황",
        description = "정책 5단계 진행 + 첨부 임베딩 + 참고 사이트 fetch 결과 대시보드"
)
public interface AdminPolicyProcessingApi {

    @Operation(
            summary = "정책 처리 현황 목록",
            description = """
                검색·필터·정렬·페이징.
                filter ∈ {ALL, INCOMPLETE, PARTIAL, RAG_FAILED, ATTACHMENT_EMBEDDING_MISSING, REFERENCE_FETCH_FAILED, GUIDE_RULE_FAILED, RECENT_24H}.
                sort ∈ {UPDATED_DESC, COMPLETENESS_ASC, ID_ASC}.

                ⚠ totalCount 는 검색·지역 필터만 적용된 모집단 크기이며, items.length 는 추가 빠른필터(완성도/단계별 FAILED 등)
                통과한 페이지 결과다. computed 필터(RAG_FAILED, ATTACHMENT_EMBEDDING_MISSING, GUIDE_RULE_FAILED, REFERENCE_FETCH_FAILED)
                는 페이지 후 in-memory 로 적용되므로 페이지마다 결과가 0 ~ size 사이일 수 있다.

                ⚠ COMPLETENESS_ASC 는 페이지 안에서만 INCOMPLETE → PARTIAL → COMPLETE 순으로 정렬된다 (in-memory 후처리).
                전역 정렬은 보장되지 않는다."""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)")
    })
    ResponseEntity<PolicyProcessingListResponse> getPolicies(
            @Parameter(description = "검색어 (정책 ID 또는 제목 부분 일치)")
            @RequestParam(required = false) String q,
            @Parameter(description = "지역 코드 필터")
            @RequestParam(required = false) String region,
            @Parameter(description = "처리 상태 필터")
            @RequestParam(defaultValue = "ALL") String filter,
            @Parameter(description = "정렬")
            @RequestParam(defaultValue = "UPDATED_DESC") String sort,
            @Parameter(description = "페이지 번호 (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (max 200)")
            @RequestParam(defaultValue = "50") int size
    );

    @Operation(
            summary = "정책 처리 현황 KPI",
            description = "완전/부분/미흡/최근 24h 카운트 (대시보드 상단 카드 4개용)."
    )
    ResponseEntity<PolicyProcessingStatsResponse> getStats();

    @Operation(
            summary = "정책 처리 현황 상세",
            description = "5단계 + 첨부별 + 참조 사이트별 상세 (목록 펼침 영역)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없습니다 (YF-004)")
    })
    ResponseEntity<PolicyProcessingDetailResponse> getDetail(
            @Parameter(description = "정책 ID") @PathVariable Long id
    );

    @Operation(
            summary = "단계 재실행",
            description = "ENRICHMENT/GUIDE/RULE/RAG_INDEXING 중 1단계 재실행. INGESTION 은 미지원 (400)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)"),
            @ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없습니다 (YF-004)"),
            @ApiResponse(responseCode = "409", description = "이미 존재하는 리소스입니다 (YF-005)")
    })
    ResponseEntity<ReprocessResponse> retryStep(
            @Parameter(description = "정책 ID") @PathVariable Long id,
            @Parameter(description = "재실행할 단계 (ENRICHMENT/GUIDE/RULE/RAG_INDEXING)")
            @PathVariable String step
    );

    @Operation(
            summary = "첨부 1건 임베딩 재실행",
            description = "AttachmentReindexService 재사용."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없습니다 (YF-004)")
    })
    ResponseEntity<ReprocessResponse> reindexAttachment(
            @Parameter(description = "정책 ID") @PathVariable Long id,
            @Parameter(description = "첨부 ID") @PathVariable Long attachmentId
    );

    @Operation(
            summary = "정책의 모든 첨부 임베딩 재실행"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없습니다 (YF-004)")
    })
    ResponseEntity<ReprocessResponse> reindexAllAttachments(
            @Parameter(description = "정책 ID") @PathVariable Long id
    );

    @Operation(
            summary = "정책 RAG 본문 재인덱싱",
            description = "RagIndexingService.requestIndexing 재사용 + RAG_INDEXING step 기록."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없습니다 (YF-004)")
    })
    ResponseEntity<ReprocessResponse> reindexRag(
            @Parameter(description = "정책 ID") @PathVariable Long id
    );

    @Operation(
            summary = "전체 재처리",
            description = "ENRICHMENT/GUIDE/RULE/RAG 모두 재실행 큐잉. 운영 추적을 위해 reason 필드 필수."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)"),
            @ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없습니다 (YF-004)")
    })
    ResponseEntity<ReprocessResponse> reprocess(
            @Parameter(description = "정책 ID") @PathVariable Long id,
            @RequestBody @Valid ReprocessPolicyRequest request
    );
}
