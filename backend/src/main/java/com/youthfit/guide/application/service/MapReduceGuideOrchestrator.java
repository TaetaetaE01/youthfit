package com.youthfit.guide.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.common.util.TokenCounter;
import com.youthfit.guide.application.dto.command.ChunkInput;
import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.application.port.GuideLlmProvider;
import com.youthfit.guide.domain.model.GuideContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MapReduceGuideOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MapReduceGuideOrchestrator.class);

    private final GuideLlmProvider llmProvider;
    private final TokenCounter tokenCounter;
    private final String model;
    private final int groupBudgetTokens;

    public MapReduceGuideOrchestrator(
            GuideLlmProvider llmProvider,
            TokenCounter tokenCounter,
            @Value("${openai.chat.model:gpt-4o-mini}") String model,
            @Value("${guide.map-reduce.group-budget-tokens:60000}") int groupBudgetTokens) {
        this.llmProvider = llmProvider;
        this.tokenCounter = tokenCounter;
        this.model = model;
        this.groupBudgetTokens = groupBudgetTokens;
    }

    public GuideContent generate(GuideGenerationInput input) {
        List<List<ChunkInput>> groups = groupChunksByTokenBudget(input, groupBudgetTokens);
        log.info("map-reduce 진입: policyId={}, groups={}, totalChunks={}",
                input.policyId(), groups.size(), input.chunks().size());

        List<GuideContent> partials = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            try {
                GuideContent partial = llmProvider.generatePartialGuide(input.withChunks(groups.get(i)));
                partials.add(partial);
            } catch (Exception e) {
                log.warn("부분 가이드 호출 실패 (retry 소진 또는 영구 실패 - skip): policyId={}, groupIndex={}, err={}",
                        input.policyId(), i, e.getMessage());
            }
        }

        if (partials.isEmpty()) {
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR,
                    "모든 부분 가이드 호출이 실패하여 가이드를 생성할 수 없습니다: policyId=" + input.policyId());
        }

        log.info("map-reduce merge 시작: policyId={}, successPartials={}/{}",
                input.policyId(), partials.size(), groups.size());
        return llmProvider.mergePartialGuides(input.withChunks(List.of()), partials);
    }

    private List<List<ChunkInput>> groupChunksByTokenBudget(GuideGenerationInput input, int budget) {
        List<List<ChunkInput>> groups = new ArrayList<>();
        List<ChunkInput> current = new ArrayList<>();
        for (ChunkInput c : input.chunks()) {
            List<ChunkInput> candidate = new ArrayList<>(current);
            candidate.add(c);
            int candidateTokens = tokenCounter.countTokens(
                    input.withChunks(candidate).combinedSourceText(), model);
            if (candidateTokens > budget && !current.isEmpty()) {
                groups.add(current);
                current = new ArrayList<>();
                current.add(c);
            } else {
                current = candidate;
            }
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        // Warn on any group whose single-chunk input exceeds budget by itself
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).size() == 1) {
                int tokens = tokenCounter.countTokens(
                        input.withChunks(groups.get(i)).combinedSourceText(), model);
                if (tokens > budget) {
                    log.warn("partial 그룹의 단일 청크가 budget 초과: policyId={}, groupIndex={}, tokens={}, budget={}",
                            input.policyId(), i, tokens, budget);
                }
            }
        }
        return groups;
    }
}
