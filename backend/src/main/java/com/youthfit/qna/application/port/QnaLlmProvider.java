package com.youthfit.qna.application.port;

import com.youthfit.qna.application.dto.command.PolicyMetadata;

import java.util.function.Consumer;

public interface QnaLlmProvider {

    String generateAnswer(String policyTitle, PolicyMetadata metadata, String context, String question, Consumer<String> chunkConsumer);
}
