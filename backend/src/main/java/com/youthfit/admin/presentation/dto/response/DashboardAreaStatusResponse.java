package com.youthfit.admin.presentation.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 어드민 대시보드 영역 카드 응답 DTO.
 *
 * <p>{@code status} 는 {@code AreaStatus} 의 enum 이름 문자열(OK / WARN / CRITICAL)이다.
 * 프론트에서 색상 매핑을 직접 수행하므로 문자열로 노출한다.</p>
 *
 * @param key        영역 식별자 (예: {@code ingestion})
 * @param label      카드 헤더 라벨
 * @param status     상태 enum 이름 (OK / WARN / CRITICAL)
 * @param summary    상태 요약 문구 (정상 / 주의 / 확인 필요)
 * @param sparkline  최근 추이 스파크라인 데이터 (v1 은 빈 리스트, v1.1 채움 예정)
 * @param deeplink   어드민 UI 상세 페이지 경로
 */
public record DashboardAreaStatusResponse(
        String key,
        String label,
        String status,
        String summary,
        List<BigDecimal> sparkline,
        String deeplink
) {
}
