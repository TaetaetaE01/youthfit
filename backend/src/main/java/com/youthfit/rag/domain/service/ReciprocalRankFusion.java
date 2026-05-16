package com.youthfit.rag.domain.service;

import com.youthfit.rag.domain.model.SimilarChunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion 으로 두 ranked list 를 결합한다.
 *
 * 점수 공식: RRF_score(chunk) = Σ 1 / (k + rank_in_each_search)
 *
 * distance 처리 규칙:
 *  - vector / trigram 양쪽 모두 SimilarChunk.distance 는 도메인 계약 (0=가까움) 을 따른다.
 *  - trigram similarity → distance 변환은 Repository 레이어 (PolicyDocumentRepositoryImpl#toTrigramChunk)
 *    에서 수행되므로 RRF 는 distance 를 그대로 보존한다.
 *  - 양쪽 모두 매칭되는 청크는 먼저 등록된 vector 의 SimilarChunk (distance 포함) 가 유지된다.
 */
public class ReciprocalRankFusion {

    public List<SimilarChunk> merge(
            List<SimilarChunk> vectorRanks,
            List<SimilarChunk> trigramRanks,
            int k,
            int topK
    ) {
        Map<Long, Double> scores = new LinkedHashMap<>();
        Map<Long, SimilarChunk> chunkById = new LinkedHashMap<>();

        // vector 우선 — 양쪽 매칭 시 vector chunk (distance 포함) 가 유지됨
        for (int rank = 0; rank < vectorRanks.size(); rank++) {
            SimilarChunk c = vectorRanks.get(rank);
            scores.merge(c.id(), 1.0 / (k + rank), Double::sum);
            chunkById.putIfAbsent(c.id(), c);
        }
        for (int rank = 0; rank < trigramRanks.size(); rank++) {
            SimilarChunk c = trigramRanks.get(rank);
            scores.merge(c.id(), 1.0 / (k + rank), Double::sum);
            chunkById.putIfAbsent(c.id(), c);
        }

        List<Map.Entry<Long, Double>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Map.Entry.<Long, Double>comparingByValue().reversed());

        List<SimilarChunk> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, sorted.size()); i++) {
            result.add(chunkById.get(sorted.get(i).getKey()));
        }
        return result;
    }
}
