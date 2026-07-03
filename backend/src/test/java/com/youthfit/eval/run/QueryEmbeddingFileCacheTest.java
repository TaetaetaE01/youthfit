package com.youthfit.eval.run;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QueryEmbeddingFileCache")
class QueryEmbeddingFileCacheTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("최초 호출은 embedFn 실행, 재호출은 캐시 히트로 호출 0회")
    void cachesAcrossInstances() {
        AtomicInteger calls = new AtomicInteger();
        float[] vector = {0.1f, 0.2f, 0.3f};

        QueryEmbeddingFileCache cache1 = new QueryEmbeddingFileCache(tempDir, "test-model");
        float[] first = cache1.getOrCompute("재학생도 되나요?", q -> {
            calls.incrementAndGet();
            return vector;
        });
        cache1.save();

        QueryEmbeddingFileCache cache2 = new QueryEmbeddingFileCache(tempDir, "test-model");
        float[] second = cache2.getOrCompute("재학생도 되나요?", q -> {
            calls.incrementAndGet();
            return new float[]{9f};
        });

        assertThat(calls.get()).isEqualTo(1);
        assertThat(first).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(second).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(Files.exists(tempDir.resolve("embeddings-test-model.json"))).isTrue();
    }

    @Test
    @DisplayName("모델이 다르면 캐시를 공유하지 않는다")
    void separateFilePerModel() {
        QueryEmbeddingFileCache cacheA = new QueryEmbeddingFileCache(tempDir, "model-a");
        cacheA.getOrCompute("질문", q -> new float[]{1f});
        cacheA.save();

        AtomicInteger calls = new AtomicInteger();
        QueryEmbeddingFileCache cacheB = new QueryEmbeddingFileCache(tempDir, "model-b");
        cacheB.getOrCompute("질문", q -> {
            calls.incrementAndGet();
            return new float[]{2f};
        });

        assertThat(calls.get()).isEqualTo(1);
    }
}
