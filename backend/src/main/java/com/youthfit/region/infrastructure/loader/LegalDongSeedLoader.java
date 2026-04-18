package com.youthfit.region.infrastructure.loader;

import com.youthfit.region.domain.model.LegalDong;
import com.youthfit.region.domain.model.RegionLevel;
import com.youthfit.region.domain.repository.LegalDongRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LegalDongSeedLoader implements ApplicationRunner {

    private static final String SEED_PATH = "seed/legal_dong.csv";

    private final LegalDongRepository legalDongRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long existing = legalDongRepository.count();
        if (existing > 0) {
            log.info("LegalDong seed skipped — {} rows already present", existing);
            return;
        }

        List<LegalDong> rows = parseCsv();
        legalDongRepository.saveAll(rows);
        log.info("LegalDong seed loaded — {} rows", rows.size());
    }

    private List<LegalDong> parseCsv() {
        ClassPathResource resource = new ClassPathResource(SEED_PATH);
        List<LegalDong> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String header = reader.readLine();
            if (header == null) {
                throw new IllegalStateException("legal_dong.csv is empty");
            }

            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;

                String[] cols = line.split(",", -1);
                if (cols.length < 5) {
                    throw new IllegalStateException("Malformed CSV at line " + lineNo + ": " + line);
                }

                String code = cols[0].trim();
                String sidoName = cols[1].trim();
                String sigunguName = cols[2].trim();
                RegionLevel level = RegionLevel.valueOf(cols[3].trim());
                String parentCode = cols[4].trim();

                rows.add(LegalDong.builder()
                        .code(code)
                        .sidoName(sidoName)
                        .sigunguName(sigunguName.isEmpty() ? null : sigunguName)
                        .level(level)
                        .parentCode(parentCode.isEmpty() ? null : parentCode)
                        .active(true)
                        .build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + SEED_PATH, e);
        }
        return rows;
    }
}
