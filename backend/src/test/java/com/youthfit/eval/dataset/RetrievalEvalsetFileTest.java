package com.youthfit.eval.dataset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 확정 평가셋 파일({@code eval/retrieval-evalset.json}) 자체의 무결성을 검증한다.
 * 리포에 커밋된 실제 파일을 대상으로 하므로, 사람이 candidate 를 확정본으로 승격할 때
 * 흔히 저지르는 실수(중복 id, NEGATIVE 케이스에 스니펫 오염, 정책 제목 불일치 등)를 잡는다.
 */
@DisplayName("retrieval-evalset.json 확정 파일 검증")
class RetrievalEvalsetFileTest {

    private static final Path EVALSET_PATH = Path.of("eval/retrieval-evalset.json");

    private EvalDataset loadEvalset() {
        assertThat(Files.exists(EVALSET_PATH))
                .as("확정 평가셋 파일이 존재해야 한다: %s (cwd=%s)",
                        EVALSET_PATH, Path.of("").toAbsolutePath())
                .isTrue();
        return new EvalDatasetLoader().load(EVALSET_PATH);
    }

    @Test
    @DisplayName("케이스 id 가 중복되지 않는다")
    void hasNoDuplicateCaseIds() {
        EvalDataset dataset = loadEvalset();

        List<String> ids = dataset.cases().stream().map(EvalCase::id).toList();

        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("NEGATIVE 케이스는 expectedSnippets 가 비어 있다")
    void negativeCasesHaveNoExpectedSnippets() {
        EvalDataset dataset = loadEvalset();

        List<EvalCase> negatives = dataset.cases().stream()
                .filter(c -> c.questionType() == EvalQuestionType.NEGATIVE)
                .toList();

        assertThat(negatives).isNotEmpty();
        assertThat(negatives).allSatisfy(c ->
                assertThat(c.expectedSnippets())
                        .as("NEGATIVE 케이스 %s 는 expectedSnippets 가 비어야 한다", c.id())
                        .isEmpty());
    }

    @Test
    @DisplayName("비-NEGATIVE 케이스는 expectedSnippets 가 채워져 있고 각 스니펫이 blank 가 아니다")
    void nonNegativeCasesHaveNonBlankExpectedSnippets() {
        EvalDataset dataset = loadEvalset();

        List<EvalCase> nonNegatives = dataset.cases().stream()
                .filter(c -> c.questionType() != EvalQuestionType.NEGATIVE)
                .toList();

        assertThat(nonNegatives).isNotEmpty();
        assertThat(nonNegatives).allSatisfy(c -> {
            assertThat(c.expectedSnippets())
                    .as("비-NEGATIVE 케이스 %s 는 expectedSnippets 가 비어 있으면 안 된다", c.id())
                    .isNotEmpty();
            assertThat(c.expectedSnippets()).allSatisfy(snippet ->
                    assertThat(snippet.isBlank())
                            .as("케이스 %s 의 스니펫이 blank 여서는 안 된다", c.id())
                            .isFalse());
        });
    }

    @Test
    @DisplayName("같은 policyId 의 policyTitle 은 모두 동일하다")
    void policyTitleIsConsistentPerPolicyId() {
        EvalDataset dataset = loadEvalset();

        Map<Long, String> titleByPolicyId = new HashMap<>();
        for (EvalCase c : dataset.cases()) {
            String existing = titleByPolicyId.putIfAbsent(c.policyId(), c.policyTitle());
            assertThat(existing == null || existing.equals(c.policyTitle()))
                    .as("policyId=%d 의 policyTitle 이 케이스마다 다르다: \"%s\" vs \"%s\"",
                            c.policyId(), existing, c.policyTitle())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("embeddingModel 이 blank 가 아니다")
    void embeddingModelIsNotBlank() {
        EvalDataset dataset = loadEvalset();

        assertThat(dataset.embeddingModel()).isNotBlank();
    }
}
