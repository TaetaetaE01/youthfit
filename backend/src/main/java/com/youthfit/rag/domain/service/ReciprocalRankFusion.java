package com.youthfit.rag.domain.service;

import com.youthfit.rag.application.dto.result.PolicyDocumentChunkResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion 으로 두 ranked list 를 결합한다.
 *
 * 점수 공식: RRF_score(chunk) = Σ 1 / (k + rank_in_each_search)
 *
 * distance 처리 규칙 (spec §6.4):
 *  - 양쪽 모두 매칭: vector 의 distance 유지
 *  - vector 단독: vector 의 distance 그대로
 *  - trigram 단독: 1.0 - similarity 로 변환
 */
public class ReciprocalRankFusion {

    public List<PolicyDocumentChunkResult> merge(
            List<PolicyDocumentChunkResult> vectorRanks,
            List<PolicyDocumentChunkResult> trigramRanks,
            int k,
            int topK
    ) {
        Map<Long, Double> scores = new LinkedHashMap<>();
        Map<Long, PolicyDocumentChunkResult> chunkById = new LinkedHashMap<>();
        Map<Long, Boolean> inVector = new LinkedHashMap<>();

        for (int rank = 0; rank < vectorRanks.size(); rank++) {
            PolicyDocumentChunkResult c = vectorRanks.get(rank);
            scores.merge(c.id(), 1.0 / (k + rank), Double::sum);
            chunkById.putIfAbsent(c.id(), c);
            inVector.put(c.id(), true);
        }
        for (int rank = 0; rank < trigramRanks.size(); rank++) {
            PolicyDocumentChunkResult c = trigramRanks.get(rank);
            scores.merge(c.id(), 1.0 / (k + rank), Double::sum);
            chunkById.putIfAbsent(c.id(), c);
            inVector.putIfAbsent(c.id(), false);
        }

        List<Map.Entry<Long, Double>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Map.Entry.<Long, Double>comparingByValue().reversed());

        List<PolicyDocumentChunkResult> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, sorted.size()); i++) {
            Long id = sorted.get(i).getKey();
            PolicyDocumentChunkResult original = chunkById.get(id);
            double distance = inVector.get(id)
                    ? original.distance()
                    : BigDecimal.ONE.subtract(BigDecimal.valueOf(original.distance())).doubleValue();
            result.add(new PolicyDocumentChunkResult(
                    original.id(),
                    original.policyId(),
                    original.chunkIndex(),
                    original.content(),
                    distance,
                    original.attachmentId(),
                    original.pageStart(),
                    original.pageEnd()
            ));
        }
        return result;
    }
}
