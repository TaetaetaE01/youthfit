package com.youthfit.rag.infrastructure.persistence;

import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PolicyDocumentRepositoryImpl implements PolicyDocumentRepository {

    private final PolicyDocumentJpaRepository jpaRepository;

    @Override
    public PolicyDocument save(PolicyDocument policyDocument) {
        return jpaRepository.save(policyDocument);
    }

    @Override
    public List<PolicyDocument> saveAll(List<PolicyDocument> policyDocuments) {
        return jpaRepository.saveAll(policyDocuments);
    }

    @Override
    public List<PolicyDocument> findByPolicyId(Long policyId) {
        return jpaRepository.findByPolicyId(policyId);
    }

    @Override
    public List<PolicyDocument> findByPolicyIdOrderByChunkIndex(Long policyId) {
        return jpaRepository.findByPolicyIdOrderByChunkIndex(policyId);
    }

    @Override
    public void deleteByPolicyId(Long policyId) {
        jpaRepository.deleteByPolicyId(policyId);
    }
}
