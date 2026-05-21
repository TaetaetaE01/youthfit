package com.youthfit.policy.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthfit.policy.domain.model.PolicyReferenceSite;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class N8nForceEnrichClient {

    private final RestClient restClient;
    private final String webhookUrl;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public N8nForceEnrichClient(RestClient.Builder builder,
                                @Value("${n8n.force-enrich-webhook-url}") String webhookUrl,
                                @Value("${youthfit.internal.api-key}") String apiKey,
                                ObjectMapper objectMapper) {
        this.restClient = builder.build();
        this.webhookUrl = webhookUrl;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    public void forceEnrich(Long jobId, Long policyId, List<PolicyReferenceSite> urls) {
        Map<String, Object> payload = Map.of(
                "jobId", jobId,
                "policyId", policyId,
                "urls", urls
        );
        restClient.post()
                .uri(webhookUrl)
                .header("X-Internal-Api-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
