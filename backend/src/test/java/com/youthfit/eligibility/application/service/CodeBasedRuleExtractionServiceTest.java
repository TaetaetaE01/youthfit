package com.youthfit.eligibility.application.service;

import com.youthfit.eligibility.application.dto.command.CodeBasedExtractionInput;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
import com.youthfit.eligibility.domain.service.CodeBasedRuleExtractor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class CodeBasedRuleExtractionServiceTest {

    private final CodeBasedRuleExtractor extractor = mock(CodeBasedRuleExtractor.class);
    private final EligibilityRuleRepository repository = mock(EligibilityRuleRepository.class);
    private final CodeBasedRuleExtractionService sut = new CodeBasedRuleExtractionService(extractor, repository);

    @Test
    void extractAndPersist_deletes_existing_then_saves_extracted_rules() {
        CodeBasedExtractionInput input = new CodeBasedExtractionInput(
                null, null, null, null, null, null, null, null,
                null, null, null, null, List.of());
        EligibilityRule r1 = mock(EligibilityRule.class);
        EligibilityRule r2 = mock(EligibilityRule.class);
        given(extractor.extract(99L, input)).willReturn(List.of(r1, r2));

        sut.extractAndPersist(99L, input);

        var inOrder = inOrder(repository);
        inOrder.verify(repository).deleteAllByPolicyId(99L);
        @SuppressWarnings("unchecked")
        var captor = forClass(List.class);
        inOrder.verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(r1, r2);
    }
}
