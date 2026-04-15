package com.youthfit.rag.infrastructure.external;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.rag.application.port.EmbeddingProvider;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiEmbeddingClient implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);
    private static final String EMBEDDINGS_URL = "https://api.openai.com/v1/embeddings";

    private final OpenAiEmbeddingProperties properties;
    private final RestClient restClient = RestClient.create();

    @Override
    public float[] embed(String text) {
        List<float[]> results = embedBatch(List.of(text));
        return results.get(0);
    }

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

        JsonNode response = restClient.post()
                .uri(EMBEDDINGS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.has("data")) {
            log.error("OpenAI 임베딩 API 호출 실패");
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "임베딩 생성에 실패했습니다");
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
