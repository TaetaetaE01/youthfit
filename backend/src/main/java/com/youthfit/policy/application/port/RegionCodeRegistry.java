package com.youthfit.policy.application.port;

import com.youthfit.policy.domain.model.RegionCode;

import java.util.List;
import java.util.Optional;

public interface RegionCodeRegistry {
    Optional<RegionCode> find(String code);

    List<RegionCode> findAll(List<String> codes);
}
