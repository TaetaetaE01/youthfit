package com.youthfit.rag.application.service;

import com.youthfit.rag.domain.model.SimilarChunk;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * trigram 조회 트랜잭션 경계 전용 헬퍼.
 * <p>
 * pg_trgm 미설치 등으로 이 조회가 실패해도 호출 측({@link RagSearchService} hybrid 경로)의
 * 바깥 readOnly 트랜잭션을 aborted 상태로 오염시키지 않도록 REQUIRES_NEW 로 격리한다
 * (#173). 트레이드오프: 호출마다 커넥션을 잠깐 2개(바깥 tx + 이 tx) 점유한다.
 * <p>
 * 트랜잭션 경계는 Application Service 에만 둔다는 컨벤션에 따라, Infrastructure 인
 * {@code PolicyDocumentRepositoryImpl} 대신 이 application 레이어 빈에 격리 책임을 둔다.
 */
@Service
@RequiredArgsConstructor
public class TrigramSearchExecutor {

    private final PolicyDocumentRepository policyDocumentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<SimilarChunk> searchTopByTrigram(Long policyId, String query, double threshold, int limit) {
        return policyDocumentRepository.findTopByTrigram(policyId, query, threshold, limit);
    }
}
