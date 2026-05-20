package com.youthfit.policy.domain.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RegionFilter {

    public static final String NATIONWIDE_TOKEN = "NATIONWIDE";
    private static final String NATIONWIDE_KOREAN = "전국";

    private final boolean nationwideOnly;
    private final List<String> sidoCodes;
    private final List<String> sigunguCodes;

    private RegionFilter(boolean nationwideOnly, List<String> sidoCodes, List<String> sigunguCodes) {
        this.nationwideOnly = nationwideOnly;
        this.sidoCodes = List.copyOf(sidoCodes);
        this.sigunguCodes = List.copyOf(sigunguCodes);
    }

    public static RegionFilter of(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return inactive();
        }
        Set<String> seen = new LinkedHashSet<>();
        boolean hasNationwideToken = false;
        List<String> sidos = new ArrayList<>();
        List<String> sigungus = new ArrayList<>();

        for (String raw : codes) {
            if (raw == null) continue;
            String code = raw.trim();
            if (code.isEmpty()) continue;
            if (!seen.add(code)) continue;

            if (NATIONWIDE_TOKEN.equals(code) || NATIONWIDE_KOREAN.equals(code)) {
                hasNationwideToken = true;
                continue;
            }
            if (!code.chars().allMatch(Character::isDigit)) continue;
            if (code.length() == 2) sidos.add(code);
            else if (code.length() == 5) sigungus.add(code);
            // 다른 길이는 무시
        }

        boolean nationwideOnly = hasNationwideToken && sidos.isEmpty() && sigungus.isEmpty();
        if (!hasNationwideToken && sidos.isEmpty() && sigungus.isEmpty()) {
            return inactive();
        }
        return new RegionFilter(nationwideOnly, sidos, sigungus);
    }

    public static RegionFilter ofCsv(String csv) {
        if (csv == null || csv.isBlank()) return inactive();
        return of(Arrays.asList(csv.split(",")));
    }

    private static RegionFilter inactive() {
        return new RegionFilter(false, List.of(), List.of());
    }

    public boolean isActive() {
        return nationwideOnly || !sidoCodes.isEmpty() || !sigunguCodes.isEmpty();
    }

    public boolean isNationwideOnly() {
        return nationwideOnly;
    }

    public List<String> sidoCodes() {
        return sidoCodes;
    }

    public List<String> sigunguCodes() {
        return sigunguCodes;
    }
}
