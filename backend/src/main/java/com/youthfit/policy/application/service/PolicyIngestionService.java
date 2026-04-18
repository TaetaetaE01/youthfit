package com.youthfit.policy.application.service;

import com.youthfit.policy.application.dto.command.RegisterPolicyCommand;
import com.youthfit.policy.application.dto.result.PolicyIngestionResult;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyApplyMethod;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.model.PolicyReferenceSite;
import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.domain.repository.PolicySourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
                Policy policy = source.getPolicy();
                policy.updateInfo(
                        command.title(),
                        command.summary(),
                        command.body(),
                        command.supportTarget(),
                        command.selectionCriteria(),
                        command.supportContent(),
                        command.organization(),
                        command.contact(),
                        command.category(),
                        command.regionCode(),
                        command.applyStart(),
                        command.applyEnd(),
                        command.referenceYear(),
                        command.supportCycle(),
                        command.provideType()
                );
                policy.replaceTags(command.lifeTags(), command.themeTags(), command.targetTags());
                policy.replaceAttachments(toAttachments(command.attachments()));
                policy.replaceReferenceSites(toReferenceSites(command.referenceSites()));
                policy.replaceApplyMethods(toApplyMethods(command.applyMethods()));
            }
            return new PolicyIngestionResult(source.getPolicy().getId(), false);
        }

        Policy policy = Policy.builder()
                .title(command.title())
                .summary(command.summary())
                .body(command.body())
                .supportTarget(command.supportTarget())
                .selectionCriteria(command.selectionCriteria())
                .supportContent(command.supportContent())
                .organization(command.organization())
                .contact(command.contact())
                .category(command.category())
                .regionCode(command.regionCode())
                .applyStart(command.applyStart())
                .applyEnd(command.applyEnd())
                .referenceYear(command.referenceYear())
                .supportCycle(command.supportCycle())
                .provideType(command.provideType())
                .build();
        policy.replaceTags(command.lifeTags(), command.themeTags(), command.targetTags());
        policy.replaceAttachments(toAttachments(command.attachments()));
        policy.replaceReferenceSites(toReferenceSites(command.referenceSites()));
        policy.replaceApplyMethods(toApplyMethods(command.applyMethods()));
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

    private List<PolicyAttachment> toAttachments(List<RegisterPolicyCommand.Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) return List.of();
        return attachments.stream()
                .map(a -> PolicyAttachment.builder()
                        .name(a.name())
                        .url(a.url())
                        .mediaType(a.mediaType())
                        .build())
                .toList();
    }

    private List<PolicyReferenceSite> toReferenceSites(List<RegisterPolicyCommand.ReferenceSite> sites) {
        if (sites == null || sites.isEmpty()) return List.of();
        return sites.stream()
                .map(s -> new PolicyReferenceSite(s.name(), s.url()))
                .toList();
    }

    private List<PolicyApplyMethod> toApplyMethods(List<RegisterPolicyCommand.ApplyMethod> methods) {
        if (methods == null || methods.isEmpty()) return List.of();
        return methods.stream()
                .map(m -> new PolicyApplyMethod(m.stageName(), m.description()))
                .toList();
    }
}
