package com.youthfit.eligibility.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "eligibility_rule")
public class EligibilityRule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(nullable = false, length = 30)
    private String field;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleOperator operator;

    @Column(nullable = false)
    private String value;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(name = "source_reference", columnDefinition = "TEXT")
    private String sourceReference;

    @Builder
    private EligibilityRule(Long policyId, String field, RuleOperator operator,
                            String value, String label, String sourceReference) {
        this.policyId = policyId;
        this.field = field;
        this.operator = operator;
        this.value = value;
        this.label = label;
        this.sourceReference = sourceReference;
    }
}
