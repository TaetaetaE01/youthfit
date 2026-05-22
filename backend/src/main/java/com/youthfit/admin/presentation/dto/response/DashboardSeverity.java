package com.youthfit.admin.presentation.dto.response;

/**
 * 어드민 대시보드 신호의 심각도.
 *
 * <p>선언 순서가 곧 우선순위다. {@code Comparator.comparing(...)} 으로 정렬 시
 * ordinal 이 낮은 {@link #HIGH} 가 먼저 위치한다. 따라서 새로운 값을
 * 추가할 때 순서를 임의로 바꾸지 않는다.</p>
 */
public enum DashboardSeverity {
    HIGH,
    MEDIUM
}
