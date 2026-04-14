package com.youthfit.policy.application.service;

import com.youthfit.policy.application.dto.command.RegisterPolicyCommand;
import com.youthfit.policy.application.dto.result.PolicyIngestionResult;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.domain.repository.PolicySourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class PolicyIngestionService {

    private final PolicyRepository policyRepository;
    private final PolicySourceRepository policySourceRepository;

    public PolicyIngestionResult registerPolicy(RegisterPolicyCommand command) {
        Optional<PolicySource> existingSource = policySourceRepository
                .findBySourceTypeAndExternalId(command.sourceType(), command.externalId());

        if (existingSource.isPresent()) {
            PolicySource source = existingSource.get();
            if (source.hasChanged(command.sourceHash())) {
                source.updateSource(command.rawJson(), command.sourceHash());
                source.getPolicy().updateInfo(
                        command.title(),
                        command.summary(),
                        command.category(),
                        command.regionCode(),
                        command.applyStart(),
                        command.applyEnd()
                );
            }
            return new PolicyIngestionResult(source.getPolicy().getId(), false);
        }

        Policy policy = Policy.builder()
                .title(command.title())
                .summary(command.summary())
                .category(command.category())
                .regionCode(command.regionCode())
                .applyStart(command.applyStart())
                .applyEnd(command.applyEnd())
                .build();
        Policy savedPolicy = policyRepository.save(policy);

        PolicySource policySource = PolicySource.builder()
                .policy(savedPolicy)
                .sourceType(command.sourceType())
                .externalId(command.externalId())
                .sourceUrl(command.sourceUrl())
                .rawJson(command.rawJson())
                .sourceHash(command.sourceHash())
                .build();
        policySourceRepository.save(policySource);

        return new PolicyIngestionResult(savedPolicy.getId(), true);
    }
}
