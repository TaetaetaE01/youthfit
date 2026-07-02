package com.youthfit.eval.report;

import com.youthfit.eval.dataset.EvalQuestionType;
import com.youthfit.eval.run.ScenarioMetrics;
import com.youthfit.eval.run.TypeMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EvalReportWriter")
class EvalReportWriterTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("리포트를 <timestamp>-<label>.json 으로 저장한다")
    void writesReportFile() throws Exception {
        TypeMetrics overall = new TypeMetrics(2, Map.of(1, 0.5, 3, 1.0, 5, 1.0, 10, 1.0), 0.75);
        ScenarioMetrics metrics = new ScenarioMetrics("baseline", 3, 2, overall,
                Map.of(EvalQuestionType.KEYWORD, overall), 0.4, 0.7, null, 42.0);
        EvalRunReport report = new EvalRunReport("test-run", "20260702-120000",
                "eval/retrieval-evalset.json", 1,
                List.of(new ScenarioReport("baseline", null, metrics, List.of())));

        Path written = new EvalReportWriter().write(report, tempDir);

        assertThat(written.getFileName().toString()).isEqualTo("20260702-120000-test-run.json");
        String json = Files.readString(written);
        assertThat(json).contains("\"scenario\"");
        assertThat(json).contains("baseline");
        assertThat(json).contains("0.75");
    }
}
