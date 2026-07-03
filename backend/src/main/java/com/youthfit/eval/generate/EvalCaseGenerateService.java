package com.youthfit.eval.generate;

import com.youthfit.eval.config.EvalProperties;
import com.youthfit.eval.dataset.EvalCase;
import com.youthfit.eval.dataset.EvalDataset;
import com.youthfit.eval.dataset.EvalDatasetLoader;
import com.youthfit.eval.dataset.EvalQuestionType;
import com.youthfit.eval.dataset.SnippetMatcher;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.SourceType;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * generate 모드: 소스별 정책을 샘플링해 LLM 역질문 후보를 만들고
 * candidate JSON 으로 출력한다. 사람 검수 후 retrieval-evalset.json 으로 확정.
 */
@Service
@Profile("eval")
@RequiredArgsConstructor
public class EvalCaseGenerateService {

    private static final Logger log = LoggerFactory.getLogger(EvalCaseGenerateService.class);
    private static final int CHUNKS_PER_POLICY = 3;
    private static final int DEFAULT_DATASET_VERSION = 1;
    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";

    private final PolicyRepository policyRepository;
    private final PolicyDocumentRepository policyDocumentRepository;
    private final EvalQuestionLlm evalQuestionLlm;
    private final EvalProperties properties;

    /**
     * @param confirm false 면 dry-run — 대상 정책·예상 LLM 호출 수만 출력하고 종료 (null 반환)
     * @param maxPerSourceOverride null 이면 properties 값 사용
     * @return 작성된 candidate JSON 경로 (dry-run 은 null)
     */
    public Path generateCandidates(boolean confirm, Integer maxPerSourceOverride) {
        int maxPerSource = maxPerSourceOverride != null
                ? maxPerSourceOverride
                : properties.generate().maxPerSource();

        // 증분 생성: 기존 candidate 에 이미 있는 정책은 스킵 (재실행 시 중복 호출 방지)
        EvalDataset existingCandidate = loadExistingCandidate();
        List<EvalCase> existingCases = existingCandidate.cases();
        java.util.Set<Long> coveredPolicyIds = existingCases.stream()
                .map(EvalCase::policyId)
                .collect(java.util.stream.Collectors.toSet());

        List<Policy> targets = sampleTargets(maxPerSource).stream()
                .filter(p -> !coveredPolicyIds.contains(p.getId()))
                .toList();
        log.info("generate 대상: 신규 정책 {}건 (기존 candidate {}건 스킵), 예상 LLM 호출 {}회 (모델 {})",
                targets.size(), coveredPolicyIds.size(), targets.size(), properties.generate().model());

        if (!confirm) {
            targets.forEach(p -> log.info("  - id={}, title={}", p.getId(), p.getTitle()));
            log.info("dry-run 종료. 실제 생성하려면 --eval.confirm=true 를 추가하세요.");
            return null;
        }

        List<EvalCase> cases = new ArrayList<>();
        List<String> failedPolicies = new ArrayList<>();
        for (Policy policy : targets) {
            List<PolicyDocument> chunks =
                    policyDocumentRepository.findByPolicyIdOrderByChunkIndex(policy.getId());
            List<String> contents = chunks.stream()
                    .limit(CHUNKS_PER_POLICY)
                    .map(PolicyDocument::getContent)
                    .toList();

            List<GeneratedEvalQuestion> generated =
                    evalQuestionLlm.generateQuestions(policy.getTitle(), contents);
            if (generated.isEmpty()) {
                failedPolicies.add(policy.getId() + ":" + policy.getTitle());
            }

            int q = 1;
            for (GeneratedEvalQuestion g : generated) {
                boolean snippetVerified = contents.stream()
                        .anyMatch(content -> SnippetMatcher.containsSnippet(content, g.snippet()));
                if (!snippetVerified) {
                    log.warn("스니펫 원문 불일치로 제외 (환각 의심): policyId={}, question=\"{}\"",
                            policy.getId(), g.question());
                    continue;
                }
                cases.add(new EvalCase(
                        "p" + policy.getId() + "-q" + q++,
                        policy.getId(), policy.getTitle(), g.question(), g.questionType(),
                        List.of(g.snippet()), null));
            }

            cases.add(new EvalCase(
                    "p" + policy.getId() + "-neg",
                    policy.getId(), policy.getTitle(),
                    NegativeQuestionPool.pick(policy.getId()),
                    EvalQuestionType.NEGATIVE, List.of(), null));
        }

        if (!failedPolicies.isEmpty()) {
            log.warn("생성 실패(스킵) 정책 {}건: {}", failedPolicies.size(), failedPolicies);
        }

        List<EvalCase> merged = new ArrayList<>(existingCases);
        merged.addAll(cases);
        return writeCandidate(new EvalDataset(
                existingCandidate.version(), existingCandidate.embeddingModel(), merged));
    }

    /** 기존 candidate 파일이 있으면 그 version/embeddingModel/cases 를 보존, 없으면 기본값으로 빈 데이터셋을 반환한다. */
    private EvalDataset loadExistingCandidate() {
        Path path = Path.of(properties.candidatePath());
        if (!Files.exists(path)) {
            return new EvalDataset(DEFAULT_DATASET_VERSION, DEFAULT_EMBEDDING_MODEL, List.of());
        }
        return new EvalDatasetLoader().load(path);
    }

    private List<Policy> sampleTargets(int maxPerSource) {
        List<Policy> targets = new ArrayList<>();
        for (SourceType source : SourceType.values()) {
            List<Policy> withChunks = policyRepository
                    .findAllByFilters(null, null, null, source, PageRequest.of(0, maxPerSource * 3))
                    .getContent().stream()
                    .filter(p -> !policyDocumentRepository
                            .findByPolicyIdOrderByChunkIndex(p.getId()).isEmpty())
                    .limit(maxPerSource)
                    .toList();
            targets.addAll(withChunks);
        }
        return targets;
    }

    private Path writeCandidate(EvalDataset dataset) {
        Path path = Path.of(properties.candidatePath());
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            ObjectMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .build();
            Files.writeString(path, mapper.writeValueAsString(dataset));
            log.info("candidate 작성 완료: {} ({}케이스). 검수 후 {} 로 확정하세요.",
                    path.toAbsolutePath(), dataset.cases().size(), properties.datasetPath());
            return path;
        } catch (Exception e) {
            throw new IllegalStateException("candidate 저장 실패: " + path, e);
        }
    }
}
