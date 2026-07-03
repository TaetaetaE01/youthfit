package com.youthfit.eval;

import com.youthfit.eval.config.EvalProperties;
import com.youthfit.eval.dataset.EvalDataset;
import com.youthfit.eval.dataset.EvalDatasetLoader;
import com.youthfit.eval.generate.EvalCaseGenerateService;
import com.youthfit.eval.reindex.EvalReindexService;
import com.youthfit.eval.report.CaseResultRow;
import com.youthfit.eval.report.EvalReportWriter;
import com.youthfit.eval.report.EvalRunReport;
import com.youthfit.eval.report.ScenarioReport;
import com.youthfit.eval.run.CaseResult;
import com.youthfit.eval.run.CaseStatus;
import com.youthfit.eval.run.EvalMetricsCalculator;
import com.youthfit.eval.run.EvalScenario;
import com.youthfit.eval.run.QueryEmbeddingFileCache;
import com.youthfit.eval.run.RetrievalEvaluator;
import com.youthfit.eval.run.ScenarioMetrics;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.qna.infrastructure.config.QnaProperties;
import com.youthfit.rag.infrastructure.external.OpenAiEmbeddingProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * eval 프로파일 진입점.
 *
 * generate: SPRING_PROFILES_ACTIVE=eval ./gradlew bootRun --args='--eval.mode=generate --eval.confirm=true'
 * run:      SPRING_PROFILES_ACTIVE=eval ./gradlew bootRun --args='--eval.mode=run --eval.scenarios=baseline,hybrid-on'
 * reindex:  SPRING_PROFILES_ACTIVE=eval ./gradlew bootRun --args='--eval.mode=reindex --eval.confirm=true'
 */
@Component
@Profile("eval")
@RequiredArgsConstructor
public class EvalRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final EvalCaseGenerateService generateService;
    private final RetrievalEvaluator retrievalEvaluator;
    private final EvalProperties evalProperties;
    private final QnaProperties qnaProperties;
    private final EvalReindexService evalReindexService;
    // eval→rag infra 의도적 참조: 실제 호출 모델을 캐시 라벨의 단일 소스로 사용 (#167)
    private final OpenAiEmbeddingProperties embeddingProperties;
    private ConfigurableApplicationContext context;

    @org.springframework.beans.factory.annotation.Autowired
    public void setContext(ConfigurableApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!evalProperties.isRunnerEnabled()) {
            return; // 통합 테스트 컨텍스트 — 자동 실행·System.exit 방지
        }
        int exitCode = 0;
        try {
            dispatch(args);
        } catch (Exception e) {
            log.error("eval 실행 실패", e);
            exitCode = 1;
        } finally {
            if (context != null) {
                int springCode = SpringApplication.exit(context, () -> 0);
                System.exit(exitCode != 0 ? exitCode : springCode);
            }
        }
    }

    void dispatch(ApplicationArguments args) {
        String mode = firstOption(args, "eval.mode");
        if (mode == null) {
            throw new IllegalArgumentException("--eval.mode=generate|run 을 지정하세요.");
        }
        switch (mode) {
            case "generate" -> {
                boolean confirm = Boolean.parseBoolean(firstOption(args, "eval.confirm"));
                String maxPerSource = firstOption(args, "eval.max-per-source");
                generateService.generateCandidates(confirm,
                        maxPerSource == null ? null : Integer.parseInt(maxPerSource));
            }
            case "run" -> runEvaluation(args);
            case "reindex" -> runReindex(args);
            default -> throw new IllegalArgumentException("알 수 없는 --eval.mode: " + mode);
        }
    }

    private void runEvaluation(ApplicationArguments args) {
        String scenariosArg = firstOption(args, "eval.scenarios");
        List<EvalScenario> scenarios = (scenariosArg == null ? List.of("baseline") : List.of(scenariosArg.split(",")))
                .stream().map(String::trim).map(EvalScenario::of).toList();
        String label = firstOption(args, "eval.label");
        if (label == null) {
            label = scenarios.stream().map(EvalScenario::name).reduce((a, b) -> a + "+" + b).orElse("run");
        }

        EvalDataset dataset = new EvalDatasetLoader().load(Path.of(evalProperties.datasetPath()));
        String cacheLabel = resolveCacheLabel(embeddingProperties.getModel(), dataset.embeddingModel());
        QueryEmbeddingFileCache cache = new QueryEmbeddingFileCache(
                Path.of(evalProperties.cacheDir()), cacheLabel);
        EvalMetricsCalculator calculator = new EvalMetricsCalculator();
        double negativeThreshold = qnaProperties.relevanceDistanceThreshold();

        List<ScenarioReport> scenarioReports = new ArrayList<>();
        for (EvalScenario scenario : scenarios) {
            log.info("시나리오 실행: {} ({}케이스)", scenario.name(), dataset.cases().size());
            List<CaseResult> results = dataset.cases().stream()
                    .map(c -> retrievalEvaluator.evaluate(c, scenario, cache))
                    .toList();
            cache.save(); // 시나리오마다 저장 — 중간 실패해도 캐시 보존

            ScenarioMetrics metrics = calculator.calculate(scenario.name(), results, negativeThreshold);
            double okRatio = results.isEmpty() ? 0.0 : metrics.okCases() / (double) results.size();
            if (okRatio < 0.9) {
                log.warn("성공 케이스 {}% (< 90%) — 평가셋 정비가 필요할 수 있습니다. STALE/NO_CHUNKS 확인.",
                        Math.round(okRatio * 100));
            }

            scenarioReports.add(new ScenarioReport(
                    scenario.name(),
                    results.stream().filter(r -> r.status() == CaseStatus.OK)
                            .findFirst().map(CaseResult::effective).orElse(null),
                    metrics,
                    results.stream().map(CaseResultRow::from).toList()));
        }

        EvalRunReport report = new EvalRunReport(label, LocalDateTime.now().format(TS),
                evalProperties.datasetPath(), dataset.version(), scenarioReports);
        EvalReportWriter writer = new EvalReportWriter();
        Path written = writer.write(report, Path.of(evalProperties.reportDir()));
        writer.printSummary(report);
        log.info("리포트 저장: {}", written.toAbsolutePath());
    }

    private void runReindex(ApplicationArguments args) {
        boolean confirm = Boolean.parseBoolean(firstOption(args, "eval.confirm"));
        String idsArg = firstOption(args, "eval.policy-ids");
        List<Long> policyIds = idsArg == null ? null
                : java.util.Arrays.stream(idsArg.split(",")).map(String::trim).map(Long::parseLong).toList();

        List<Policy> targets = evalReindexService.findTargets(policyIds);
        log.info("reindex 대상: 정책 {}건 (현재 임베딩 모델로 delete→재인덱싱, 첨부 LLM 게이트는 판정 캐시 재사용)",
                targets.size());
        if (!confirm) {
            targets.forEach(p -> log.info("  - id={}, title={}", p.getId(), p.getTitle()));
            log.info("dry-run 종료. 실제 재인덱싱하려면 --eval.confirm=true 를 추가하세요.");
            return;
        }

        long start = System.currentTimeMillis();
        List<Long> failed = new java.util.ArrayList<>();
        int done = 0;
        for (Policy policy : targets) {
            try {
                if (evalReindexService.reindexPolicy(policy.getId())) {
                    done++;
                } else {
                    failed.add(policy.getId());
                }
            } catch (Exception e) {
                log.warn("재인덱싱 실패, 계속 진행: policyId={}, error={}", policy.getId(), e.toString());
                failed.add(policy.getId());
            }
        }
        log.info("reindex 완료: 성공 {}건 / 실패 {}건 {}, 소요 {}ms",
                done, failed.size(), failed.isEmpty() ? "" : failed, System.currentTimeMillis() - start);
    }

    /** 캐시 라벨은 실제 호출 모델. evalset 라벨과 다르면 경고(평가셋 제작 기준 모델 추적용). */
    static String resolveCacheLabel(String actualModel, String datasetModel) {
        if (datasetModel != null && !datasetModel.equals(actualModel)) {
            LoggerFactory.getLogger(EvalRunner.class).warn(
                    "evalset embeddingModel({}) 과 실제 호출 모델({}) 불일치 — 캐시는 실제 모델 기준으로 분리됩니다.",
                    datasetModel, actualModel);
        }
        return actualModel;
    }

    private String firstOption(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }
}
