package com.youthfit.eval.dataset;

import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

public class EvalDatasetLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public EvalDataset load(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalStateException("평가셋 파일을 찾을 수 없습니다: " + path.toAbsolutePath());
        }
        try {
            return objectMapper.readValue(Files.readString(path), EvalDataset.class);
        } catch (Exception e) {
            throw new IllegalStateException("평가셋 파싱 실패: " + path.toAbsolutePath(), e);
        }
    }
}
