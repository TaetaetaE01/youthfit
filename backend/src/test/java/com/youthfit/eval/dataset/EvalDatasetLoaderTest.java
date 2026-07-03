package com.youthfit.eval.dataset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EvalDatasetLoader")
class EvalDatasetLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("평가셋 JSON 을 로드한다")
    void loadsDataset() throws Exception {
        Path file = tempDir.resolve("evalset.json");
        Files.writeString(file, """
                {
                  "version": 1,
                  "embeddingModel": "text-embedding-3-small",
                  "cases": [
                    {
                      "id": "p1-q1",
                      "policyId": 1,
                      "policyTitle": "청년 월세 지원",
                      "question": "재학생도 신청 가능한가요?",
                      "questionType": "KEYWORD",
                      "expectedSnippets": ["대학 재학생은 신청 대상에서 제외"],
                      "notes": null
                    }
                  ]
                }
                """);

        EvalDataset dataset = new EvalDatasetLoader().load(file);

        assertThat(dataset.version()).isEqualTo(1);
        assertThat(dataset.cases()).hasSize(1);
        EvalCase c = dataset.cases().get(0);
        assertThat(c.questionType()).isEqualTo(EvalQuestionType.KEYWORD);
        assertThat(c.expectedSnippets()).isEqualTo(List.of("대학 재학생은 신청 대상에서 제외"));
    }

    @Test
    @DisplayName("케이스에 알 수 없는 필드가 있어도 관용적으로 로드한다")
    void toleratesUnknownFields() throws Exception {
        Path file = tempDir.resolve("evalset-unknown-field.json");
        Files.writeString(file, """
                {
                  "version": 1,
                  "embeddingModel": "text-embedding-3-small",
                  "cases": [
                    {
                      "id": "p1-q1",
                      "policyId": 1,
                      "policyTitle": "청년 월세 지원",
                      "question": "재학생도 신청 가능한가요?",
                      "questionType": "KEYWORD",
                      "expectedSnippets": ["대학 재학생은 신청 대상에서 제외"],
                      "notes": null,
                      "futureField": "x"
                    }
                  ]
                }
                """);

        EvalDataset dataset = new EvalDatasetLoader().load(file);

        assertThat(dataset.cases()).hasSize(1);
        assertThat(dataset.cases().get(0).id()).isEqualTo("p1-q1");
    }

    @Test
    @DisplayName("파일이 없으면 명확한 예외")
    void failsOnMissingFile() {
        assertThatThrownBy(() -> new EvalDatasetLoader().load(tempDir.resolve("없는파일.json")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("평가셋 파일을 찾을 수 없습니다");
    }
}
