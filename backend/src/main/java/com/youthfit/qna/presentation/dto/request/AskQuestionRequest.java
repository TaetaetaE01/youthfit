package com.youthfit.qna.presentation.dto.request;

import com.youthfit.qna.application.dto.command.AskQuestionCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AskQuestionRequest(
        @NotNull(message = "정책 ID는 필수입니다")
        Long policyId,

        @NotBlank(message = "질문을 입력해 주세요")
        @Size(min = 2, max = 500, message = "질문은 2~500자여야 합니다")
        String question
) {

    public AskQuestionCommand toCommand(Long userId) {
        return new AskQuestionCommand(policyId, question, userId);
    }
}
