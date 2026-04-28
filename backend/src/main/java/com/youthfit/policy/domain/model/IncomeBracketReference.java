package com.youthfit.policy.domain.model;

import java.util.Map;
import java.util.Optional;

public record IncomeBracketReference(
        int year,
        int version,
        Map<HouseholdSize, Map<Integer, Long>> medianIncome,
        Map<HouseholdSize, Long> nearPoor
) {

    public IncomeBracketReference {
        medianIncome = medianIncome == null ? Map.of() : Map.copyOf(medianIncome);
        nearPoor = nearPoor == null ? Map.of() : Map.copyOf(nearPoor);
    }

    public Optional<Long> findAmount(HouseholdSize size, int percent) {
        Map<Integer, Long> bySize = medianIncome.get(size);
        if (bySize == null) return Optional.empty();
        return Optional.ofNullable(bySize.get(percent));
    }

    public String toContextText() {
        StringBuilder sb = new StringBuilder();
        sb.append("[참고 - 환산표 (").append(year).append("년 기준)]\n");
        appendMedian(sb, "1인 가구", HouseholdSize.ONE);
        appendMedian(sb, "2인 가구", HouseholdSize.TWO);
        sb.append("차상위계층 (기준중위소득 50%):\n  1인=")
          .append(formatManwon(nearPoor.get(HouseholdSize.ONE))).append(" / 2인=")
          .append(formatManwon(nearPoor.get(HouseholdSize.TWO))).append("\n");
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

    private String formatManwon(Long won) {
        if (won == null) return "-";
        return String.format("%.1f만", won / 10000.0);
    }
}
