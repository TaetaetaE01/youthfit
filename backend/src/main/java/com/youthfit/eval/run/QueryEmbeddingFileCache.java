package com.youthfit.eval.run;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 질문 임베딩 파일 캐시. 첫 run 만 임베딩 API 를 호출하고 이후 run 은 비용 0.
 * 파일은 git 커밋하지 않는다 (backend/eval/.gitignore).
 */
public class QueryEmbeddingFileCache {

    private static final Logger log = LoggerFactory.getLogger(QueryEmbeddingFileCache.class);

    private final Path file;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, float[]> entries;

    public QueryEmbeddingFileCache(Path cacheDir, String model) {
        this.file = cacheDir.resolve("embeddings-" + model + ".json");
        this.entries = loadExisting();
    }

    public float[] getOrCompute(String question, Function<String, float[]> embedFn) {
        return entries.computeIfAbsent(sha256(question), key -> embedFn.apply(question));
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, objectMapper.writeValueAsString(entries));
        } catch (Exception e) {
            log.warn("임베딩 캐시 저장 실패 (다음 run 에서 재호출됨): {}", file, e);
        }
    }

    private Map<String, float[]> loadExisting() {
        if (!Files.exists(file)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(Files.readString(file), new TypeReference<HashMap<String, float[]>>() {});
        } catch (Exception e) {
            log.warn("임베딩 캐시 로드 실패, 빈 캐시로 시작: {}", file, e);
            return new HashMap<>();
        }
    }

    private static String sha256(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 사용 불가", e);
        }
    }
}
