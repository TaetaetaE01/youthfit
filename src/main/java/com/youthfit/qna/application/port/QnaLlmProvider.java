package com.youthfit.qna.application.port;

import java.util.function.Consumer;

public interface QnaLlmProvider {

    String generateAnswer(String policyTitle, String context, String question, Consumer<String> chunkConsumer);
}
