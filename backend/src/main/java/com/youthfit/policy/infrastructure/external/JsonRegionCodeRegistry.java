package com.youthfit.policy.infrastructure.external;

import com.youthfit.policy.application.port.RegionCodeRegistry;
import com.youthfit.policy.domain.model.RegionCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JsonRegionCodeRegistry implements RegionCodeRegistry {

    private static final String RESOURCE = "region-codes.json";

    private final ObjectMapper objectMapper;
    private Map<String, RegionCode> map = Map.of();

    @PostConstruct
    void load() {
        try (InputStream is = new ClassPathResource(RESOURCE).getInputStream()) {
            List<RegionCode> codes = objectMapper.readValue(is, new TypeReference<List<RegionCode>>() {});
            map = codes.stream().collect(Collectors.toUnmodifiableMap(RegionCode::code, c -> c));
        } catch (Exception e) {
            throw new IllegalStateException("region-codes.json 로드 실패", e);
        }
    }

    @Override
    public Optional<RegionCode> find(String code) {
        return Optional.ofNullable(map.get(code));
    }

    @Override
    public List<RegionCode> findAll(List<String> codes) {
        if (codes == null || codes.isEmpty()) return List.of();
        return codes.stream()
                .map(map::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
