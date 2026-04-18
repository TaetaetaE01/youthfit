package com.youthfit.region.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "legal_dong",
        indexes = {
                @Index(name = "idx_legal_dong_level", columnList = "level"),
                @Index(name = "idx_legal_dong_parent_code", columnList = "parent_code")
        }
)
public class LegalDong {

    @Id
    @Column(length = 10, nullable = false)
    private String code;

    @Column(name = "sido_name", nullable = false, length = 30)
    private String sidoName;

    @Column(name = "sigungu_name", length = 40)
    private String sigunguName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RegionLevel level;

    @Column(name = "parent_code", length = 10)
    private String parentCode;

    @Column(nullable = false)
    private boolean active;

    @Builder
    private LegalDong(String code, String sidoName, String sigunguName,
                      RegionLevel level, String parentCode, boolean active) {
        this.code = code;
        this.sidoName = sidoName;
        this.sigunguName = sigunguName;
        this.level = level;
        this.parentCode = parentCode;
        this.active = active;
    }

    public String displayName() {
        return level == RegionLevel.SIDO ? sidoName : sigunguName;
    }
}
