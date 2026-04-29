package com.youthfit.policy.infrastructure.external;

import com.youthfit.policy.application.port.IncomeBracketReferenceLoader;
import com.youthfit.policy.domain.model.HouseholdSize;
import com.youthfit.policy.domain.model.IncomeBracketReference;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Component
public class YamlIncomeBracketReferenceLoader implements IncomeBracketReferenceLoader {

    private static final Logger log = LoggerFactory.getLogger(YamlIncomeBracketReferenceLoader.class);
    private static final String CLASSPATH_GLOB = "classpath:income-bracket/*.yaml";

    private final TreeMap<Integer, IncomeBracketReference> byYear = new TreeMap<>();

    @PostConstruct
    public void load() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(CLASSPATH_GLOB);
            for (Resource r : resources) {
                try (InputStream in = r.getInputStream()) {
                    IncomeBracketReference ref = parse(new Yaml().load(in));
                    byYear.put(ref.year(), ref);
                    log.info("income-bracket reference 로드: year={}, version={}", ref.year(), ref.version());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("income-bracket yaml 로드 실패", e);
        }
        if (byYear.isEmpty()) {
            throw new IllegalStateException("income-bracket yaml이 한 개도 없음");
        }
    }

    @Override
    public Optional<IncomeBracketReference> findByYear(int year) {
        return Optional.ofNullable(byYear.get(year));
    }

    @Override
    public IncomeBracketReference findLatest() {
        return byYear.lastEntry().getValue();
    }

    @SuppressWarnings("unchecked")
    private IncomeBracketReference parse(Map<String, Object> raw) {
        int year = (int) raw.get("year");
        int version = (int) raw.get("version");
        Map<String, Map<String, Number>> medianRaw = (Map<String, Map<String, Number>>) raw.get("medianIncome");
        Map<String, Number> nearPoorRaw = (Map<String, Number>) raw.get("nearPoor");
        Map<String, Number> urbanWorkerRaw = (Map<String, Number>) raw.get("urbanWorkerIncome");

        Map<HouseholdSize, Map<Integer, Long>> median = new HashMap<>();
        if (medianRaw != null) {
            medianRaw.forEach((sizeKey, byPercent) -> {
                Map<Integer, Long> mapped = new HashMap<>();
                byPercent.forEach((p, v) -> mapped.put(Integer.parseInt(p), v.longValue()));
                median.put(toSize(sizeKey), mapped);
            });
        }

        Map<HouseholdSize, Long> nearPoor = new HashMap<>();
        if (nearPoorRaw != null) {
            nearPoorRaw.forEach((k, v) -> nearPoor.put(toSize(k), v.longValue()));
        }

        Map<HouseholdSize, Long> urbanWorker = new HashMap<>();
        if (urbanWorkerRaw != null) {
            urbanWorkerRaw.forEach((k, v) -> urbanWorker.put(toSize(k), v.longValue()));
        }

        return new IncomeBracketReference(year, version, median, nearPoor, urbanWorker);
    }

    private HouseholdSize toSize(String key) {
        try {
            return HouseholdSize.fromCount(Integer.parseInt(key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("yaml household 키 파싱 실패: " + key, e);
        }
    }
}
