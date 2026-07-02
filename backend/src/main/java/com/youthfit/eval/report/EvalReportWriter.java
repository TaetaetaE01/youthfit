package com.youthfit.eval.report;

import com.youthfit.eval.run.ScenarioMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;

public class EvalReportWriter {

    private static final Logger log = LoggerFactory.getLogger(EvalReportWriter.class);

    private final ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    public Path write(EvalRunReport report, Path reportDir) {
        try {
            Files.createDirectories(reportDir);
            Path target = reportDir.resolve(report.executedAt() + "-" + report.label() + ".json");
            Files.writeString(target, objectMapper.writeValueAsString(report));
            return target;
        } catch (Exception e) {
            throw new IllegalStateException("리포트 저장 실패", e);
        }
    }

    public void printSummary(EvalRunReport report) {
        StringBuilder sb = new StringBuilder("\n=== RAG retrieval 평가 결과: ").append(report.label()).append(" ===\n");
        sb.append(String.format("%-12s %6s %6s %8s %8s %8s %8s %8s %10s %8s%n",
                "scenario", "total", "ok", "R@1", "R@3", "R@5", "R@10", "MRR", "negFP", "avgMs"));
        for (ScenarioReport s : report.scenarios()) {
            ScenarioMetrics m = s.metrics();
            sb.append(String.format("%-12s %6d %6d %8.3f %8.3f %8.3f %8.3f %8.3f %10s %8.1f%n",
                    m.scenario(), m.totalCases(), m.okCases(),
                    m.overall().recallAtK().getOrDefault(1, 0.0),
                    m.overall().recallAtK().getOrDefault(3, 0.0),
                    m.overall().recallAtK().getOrDefault(5, 0.0),
                    m.overall().recallAtK().getOrDefault(10, 0.0),
                    m.overall().mrrAt10(),
                    m.negativeFalsePositiveRate() == null ? "-" : String.format("%.3f", m.negativeFalsePositiveRate()),
                    m.avgTookMs()));
        }
        log.info(sb.toString());
    }
}
