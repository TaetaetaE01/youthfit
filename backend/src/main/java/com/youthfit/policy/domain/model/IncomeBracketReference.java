package com.youthfit.policy.domain.model;

import java.util.Map;
import java.util.Optional;

public record IncomeBracketReference(
        int year,
        int version,
        Map<HouseholdSize, Map<Integer, Long>> medianIncome,
        Map<HouseholdSize, Long> nearPoor,
        Map<HouseholdSize, Long> urbanWorkerIncome
) {

    public IncomeBracketReference {
        medianIncome = medianIncome == null ? Map.of() : Map.copyOf(medianIncome);
        nearPoor = nearPoor == null ? Map.of() : Map.copyOf(nearPoor);
        urbanWorkerIncome = urbanWorkerIncome == null ? Map.of() : Map.copyOf(urbanWorkerIncome);
    }

    /** 기존 시그니처 호환용. urbanWorkerIncome 없는 호출 케이스. */
    public IncomeBracketReference(int year, int version,
                                  Map<HouseholdSize, Map<Integer, Long>> medianIncome,
                                  Map<HouseholdSize, Long> nearPoor) {
        this(year, version, medianIncome, nearPoor, Map.of());
    }

    public Optional<Long> findAmount(HouseholdSize size, int percent) {
        Map<Integer, Long> bySize = medianIncome.get(size);
        if (bySize == null) return Optional.empty();
        return Optional.ofNullable(bySize.get(percent));
    }

    /**
     * 도시근로자 가구 월평균소득 환산. 100% 기준값에 percent/100 을 곱해 반환한다.
     * (LH/공공임대 공고에서 비율은 1% 단위로 가변이므로 base 만 저장하고 비례 계산.)
     */
    public Optional<Long> findUrbanWorkerAmount(HouseholdSize size, int percent) {
        Long base = urbanWorkerIncome.get(size);
        if (base == null) return Optional.empty();
        return Optional.of(Math.round(base * percent / 100.0));
    }

    public String toContextText() {
        StringBuilder sb = new StringBuilder();
        sb.append("[참고 - 환산표 (").append(year).append("년 기준)]\n");
        appendMedian(sb, "1인 가구", HouseholdSize.ONE);
        appendMedian(sb, "2인 가구", HouseholdSize.TWO);
        sb.append("차상위계층 (기준중위소득 50%):\n  1인=")
          .append(formatManwon(nearPoor.get(HouseholdSize.ONE))).append(" / 2인=")
          .append(formatManwon(nearPoor.get(HouseholdSize.TWO))).append("\n");
        appendUrbanWorker(sb);
        sb.append("주의: 위 값은 [원문 - 첨부]에 환산 금액이 명시되지 않은 경우에만 사용한다. 첨부에 명시된 값이 우선이다.\n");
        return sb.toString();
    }

    private void appendMedian(StringBuilder sb, String label, HouseholdSize size) {
        Map<Integer, Long> bySize = medianIncome.get(size);
        if (bySize == null || bySize.isEmpty()) return;
        sb.append("기준중위소득 (").append(label).append("):\n  ");
        bySize.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(e.getKey()).append("%=").append(formatManwon(e.getValue())).append(" / "));
        sb.setLength(sb.length() - 3); // 마지막 " / " 제거
        sb.append("\n");
    }

    private void appendUrbanWorker(StringBuilder sb) {
        if (urbanWorkerIncome.isEmpty()) return;
        sb.append("도시근로자 가구 월평균소득 100% (전년도 기준):\n  ");
        urbanWorkerIncome.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(e.getKey().count()).append("인=").append(formatManwon(e.getValue())).append(" / "));
        sb.setLength(sb.length() - 3);
        sb.append("\n  (다른 비율은 100% × percent/100 으로 계산. 예: 130% 4인 = 4인 100%값 × 1.3)\n");
    }

    private String formatManwon(Long won) {
        if (won == null) return "-";
        return String.format("%.1f만", won / 10000.0);
    }
}
