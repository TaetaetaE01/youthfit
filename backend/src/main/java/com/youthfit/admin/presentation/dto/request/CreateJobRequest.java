package com.youthfit.admin.presentation.dto.request;

import jakarta.validation.Valid;

import java.util.List;

/**
 * EnrichmentJob 생성 요청 DTO.
 * urls 가 null/empty 이면 Policy.referenceSites 를 그대로 사용한다.
 */
public record CreateJobRequest(
        @Valid List<ReferenceSiteRequest> urls
) { }
