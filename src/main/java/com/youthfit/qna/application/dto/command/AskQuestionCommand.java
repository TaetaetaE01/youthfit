package com.youthfit.qna.application.dto.command;

public record AskQuestionCommand(Long policyId, String question, Long userId) {
}
