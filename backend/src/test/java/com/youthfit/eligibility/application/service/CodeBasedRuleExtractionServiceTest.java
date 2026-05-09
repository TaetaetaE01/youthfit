package com.youthfit.eligibility.application.service;

import com.youthfit.eligibility.application.dto.command.CodeBasedExtractionInput;
import com.youthfit.eligibility.domain.model.EligibilityRule;
import com.youthfit.eligibility.domain.repository.EligibilityRuleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

class CodeBasedRuleExtractionServiceTest {

    private final EligibilityRuleRepository repository = mock(EligibilityRuleRepository.class);
    private final CodeBasedRuleExtractionService sut = new CodeBasedRuleExtractionService(repository);

    @Test
    void extractAndPersist_deletes_existing_then_saves_8_extracted_rules() {
        CodeBasedExtractionInput input = new CodeBasedExtractionInput(
                null, null, null, null, null, null, null, null,
                null, null, null, null, List.of());

        sut.extractAndPersist(99L, input);

        var inOrder = inOrder(repository);
        inOrder.verify(repository).deleteAllByPolicyId(99L);
        @SuppressWarnings("unchecked")
        var captor = forClass(List.class);
        inOrder.verify(repository).saveAll(captor.capture());

        @SuppressWarnings("unchecked")
        List<EligibilityRule> savedRules = (List<EligibilityRule>) captor.getValue();
        assertThat(savedRules).hasSize(8);
        assertThat(savedRules)
                .extracting(EligibilityRule::getField)
                .containsExactly("age", "maritalStatus", "annualIncome", "employmentKind",
                                 "education", "majorField", "specializationField", "region");
    }
}
