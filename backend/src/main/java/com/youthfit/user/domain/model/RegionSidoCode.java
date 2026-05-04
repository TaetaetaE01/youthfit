package com.youthfit.user.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RegionSidoCode implements LabeledEnum {
    SEOUL("서울", "11"),
    BUSAN("부산", "26"),
    DAEGU("대구", "27"),
    INCHEON("인천", "28"),
    GWANGJU("광주", "29"),
    DAEJEON("대전", "30"),
    ULSAN("울산", "31"),
    SEJONG("세종", "36"),
    GYEONGGI("경기", "41"),
    GANGWON("강원", "42"),
    CHUNGBUK("충북", "43"),
    CHUNGNAM("충남", "44"),
    JEONBUK("전북", "45"),
    JEONNAM("전남", "46"),
    GYEONGBUK("경북", "47"),
    GYEONGNAM("경남", "48"),
    JEJU("제주", "50");

    private final String displayName;
    private final String legalDongPrefix;

    @Override
    public String displayName() {
        return displayName;
    }

    public boolean matches(String policyRegionCode) {
        if (policyRegionCode == null || "NATIONAL".equals(policyRegionCode)) {
            return true;
        }
        if (this.name().equals(policyRegionCode)) {
            return true;
        }
        if (legalDongPrefix != null && policyRegionCode.startsWith(legalDongPrefix)) {
            return true;
        }
        return false;
    }
}
