package com.youthfit.qna.presentation.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AskQuestionRequest validation")
class AskQuestionRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("정상 길이(2~500자) 질문은 통과")
    void validQuestion_passes() {
        AskQuestionRequest request = new AskQuestionRequest(1L, "정상 질문입니다");

        Set<ConstraintViolation<AskQuestionRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("질문이 1자면 실패")
    void questionTooShort_fails() {
        AskQuestionRequest request = new AskQuestionRequest(1L, "?");

        Set<ConstraintViolation<AskQuestionRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("질문이 501자면 실패")
    void questionTooLong_fails() {
        String longQuestion = "가".repeat(501);
        AskQuestionRequest request = new AskQuestionRequest(1L, longQuestion);

        Set<ConstraintViolation<AskQuestionRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("질문이 빈 문자열이면 실패")
    void blankQuestion_fails() {
        AskQuestionRequest request = new AskQuestionRequest(1L, "   ");

        Set<ConstraintViolation<AskQuestionRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("정책 ID 가 null 이면 실패")
    void nullPolicyId_fails() {
        AskQuestionRequest request = new AskQuestionRequest(null, "정상 질문");

        Set<ConstraintViolation<AskQuestionRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }
}
