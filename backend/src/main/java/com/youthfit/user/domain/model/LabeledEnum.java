package com.youthfit.user.domain.model;

/**
 * 도메인 enum이 사용자에게 보여줄 한국어 라벨을 노출하기 위한 마커 인터페이스.
 */
public interface LabeledEnum {
    String displayName();
}
