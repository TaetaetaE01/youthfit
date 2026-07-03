package com.youthfit.eval.reindex;

import com.youthfit.ingestion.application.service.AttachmentReindexService;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 임베딩 모델 실험용 전 정책 재인덱싱(#167).
 * 삭제 후 재인덱싱이므로 source_hash 게이트(내용 기반)를 우회해
 * 현재 OPENAI_EMBEDDING_MODEL 로 임베딩을 새로 생성한다.
 */
@Service
@Profile("eval")
@RequiredArgsConstructor
public class EvalReindexService {

    private final PolicyRepository policyRepository;
    private final PolicyDocumentRepository policyDocumentRepository;
    private final AttachmentReindexService attachmentReindexService;

    /** policyIds 가 null/빈이면 청크 보유 전 정책. */
    public List<Policy> findTargets(List<Long> policyIds) {
        List<Policy> candidates = (policyIds == null || policyIds.isEmpty())
                ? policyRepository.findAllForStats()
                : policyRepository.findAllById(policyIds);
        return candidates.stream()
                .filter(p -> !policyDocumentRepository
                        .findByPolicyIdOrderByChunkIndex(p.getId()).isEmpty())
                .toList();
    }

    /**
     * 정책 1건 재인덱싱 — 삭제와 재인덱싱을 한 트랜잭션으로 묶는다
     * (실패 시 롤백돼 기존 청크가 유실되지 않음).
     */
    @Transactional
    public boolean reindexPolicy(Long policyId) {
        policyDocumentRepository.deleteByPolicyId(policyId);
        IndexingResult result = attachmentReindexService.reindexWithoutEvents(policyId);
        if (result == null) {
            // 삭제만 커밋되면 청크 유실 — 예외로 롤백 유도 (EvalRunner 가 per-policy catch)
            throw new IllegalStateException(
                    "재인덱싱 스킵(costGuard 차단 또는 정책 미존재) — 청크 삭제 롤백: policyId=" + policyId);
        }
        return true;
    }
}
