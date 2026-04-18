package com.youthfit.user.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "eligibility_profile",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_eligibility_profile_user_id",
                columnNames = "user_id"
        ),
        indexes = {
                @Index(name = "idx_eligibility_profile_legal_dong", columnList = "legal_dong_code")
        }
)
public class EligibilityProfile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "legal_dong_code", length = 10)
    private String legalDongCode;

    private Integer age;

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", length = 10)
    private MaritalStatus maritalStatus;

    @Column(name = "income_min")
    private Long incomeMin;

    @Column(name = "income_max")
    private Long incomeMax;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Education education;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_kind", length = 20)
    private EmploymentKind employmentKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "major_field", length = 20)
    private MajorField majorField;

    @Enumerated(EnumType.STRING)
    @Column(name = "specialization_field", length = 20)
    private SpecializationField specializationField;

    @Builder
    private EligibilityProfile(Long userId) {
        this.userId = userId;
    }

    public static EligibilityProfile empty(Long userId) {
        return EligibilityProfile.builder().userId(userId).build();
    }

    public void changeLegalDongCode(String legalDongCode) {
        this.legalDongCode = legalDongCode;
    }

    public void changeAge(Integer age) {
        this.age = age;
    }

    public void changeMaritalStatus(MaritalStatus maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

    public void changeIncomeRange(Long incomeMin, Long incomeMax) {
        this.incomeMin = incomeMin;
        this.incomeMax = incomeMax;
    }

    public void changeEducation(Education education) {
        this.education = education;
    }

    public void changeEmploymentKind(EmploymentKind employmentKind) {
        this.employmentKind = employmentKind;
    }

    public void changeMajorField(MajorField majorField) {
        this.majorField = majorField;
    }

    public void changeSpecializationField(SpecializationField specializationField) {
        this.specializationField = specializationField;
    }
}
