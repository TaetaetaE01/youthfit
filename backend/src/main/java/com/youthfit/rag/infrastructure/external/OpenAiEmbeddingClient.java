package com.youthfit.rag.infrastructure.external;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import com.youthfit.rag.application.port.EmbeddingProvider;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.youthfit.common.openai.OpenAiErrorClassifier;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiEmbeddingClient implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);
    private static final String EMBEDDINGS_URL = "https://api.openai.com/v1/embeddings";

    private final OpenAiEmbeddingProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final OpenAiErrorClassifier errorClassifier;
    private final RestClient restClient = RestClient.create();

    @Override
    public float[] embed(String text) {
        List<float[]> results = embedBatch(List.of(text));
        return results.get(0);
    }

    @Retry(name = "openai-embedding")
    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        Map<String, Object> requestBody = Map.of(
                "input", texts,
                "model", properties.getModel(),
                "dimensions", properties.getDimensions()
        );

        JsonNode response;
        try {
            response = restClient.post()
                    .uri(EMBEDDINGS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
            log.warn("OpenAI Embedding API 호출 실패 (분류 → retry 판정): status={}, msg={}",
                    e instanceof org.springframework.web.client.RestClientResponseException rce
                            ? rce.getStatusCode() : "n/a",
                    e.getMessage());
            throw errorClassifier.classify(e);
        }

        if (response == null || !response.has("data")) {
            log.error("OpenAI 임베딩 API 호출 실패");
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "임베딩 생성에 실패했습니다");
        }

        try {
            JsonNode usage = response.get("usage");
            int prompt = usage == null || !usage.has("prompt_tokens") ? 0 : usage.get("prompt_tokens").asInt();
            eventPublisher.publishEvent(new LlmCallRecorded(
                    LlmModule.EMBEDDING, properties.getModel(), prompt, 0, Instant.now()
            ));
        } catch (Exception e) {
            log.warn("embedding LLM 비용 이벤트 발행 실패 (정상 흐름 진행)", e);
        }

        List<float[]> embeddings = new ArrayList<>();
        for (JsonNode item : response.get("data")) {
            JsonNode embeddingNode = item.get("embedding");
            float[] vector = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vector[i] = embeddingNode.get(i).floatValue();
            }
            embeddings.add(vector);
        }

        return embeddings;
    }
}
