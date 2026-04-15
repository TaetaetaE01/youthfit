package com.youthfit.qna.presentation.dto.request;

import com.youthfit.qna.application.dto.command.AskQuestionCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AskQuestionRequest(
        @NotNull(message = "정책 ID는 필수입니다")
        Long policyId,

        @NotBlank(message = "질문을 입력해 주세요")
        String question
) {

    public AskQuestionCommand toCommand(Long userId) {
        return new AskQuestionCommand(policyId, question, userId);
    }
}
