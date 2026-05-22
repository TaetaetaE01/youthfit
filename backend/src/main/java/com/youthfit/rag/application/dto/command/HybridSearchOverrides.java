package com.youthfit.rag.application.dto.command;

/**
 * 어드민 미리보기 도구에서 baseline (yml) 위에 부분 덮어쓰기할 값.
 * 모든 필드 nullable — null 이면 baseline 값 사용.
 */
public record HybridSearchOverrides(
        Boolean hybridEnabled,
        Integer topNPerSearch,
        Integer rrfK,
        Double trigramThreshold,
        Boolean keywordBoostEnabled,
        Integer maxKeywords
) {}
