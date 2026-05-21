package com.youthfit.policy.infrastructure.external;

import com.youthfit.policy.application.port.ForceEnrichTrigger;
import com.youthfit.policy.domain.model.PolicyReferenceSite;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class N8nForceEnrichClient implements ForceEnrichTrigger {

    private final RestClient restClient;
    private final String webhookUrl;
    private final String apiKey;

    public N8nForceEnrichClient(@Value("${n8n.force-enrich-webhook-url}") String webhookUrl,
                                @Value("${youthfit.internal.api-key}") String apiKey,
                                @Value("${n8n.force-enrich.connect-timeout-seconds:5}") int connectTimeoutSeconds,
                                @Value("${n8n.force-enrich.read-timeout-seconds:30}") int readTimeoutSeconds) {
        this(buildRestClient(connectTimeoutSeconds, readTimeoutSeconds), webhookUrl, apiKey);
    }

    // 테스트 전용: MockRestServiceServer 와 함께 사용한다.
    N8nForceEnrichClient(RestClient restClient, String webhookUrl, String apiKey) {
        this.restClient = restClient;
        this.webhookUrl = webhookUrl;
        this.apiKey = apiKey;
    }

    private static RestClient buildRestClient(int connectTimeoutSeconds, int readTimeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public void forceEnrich(Long jobId, Long policyId, List<PolicyReferenceSite> urls) {
        Map<String, Object> payload = Map.of(
                "jobId", jobId,
                "policyId", policyId,
                "urls", urls
        );
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .header("X-Internal-Api-Key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new EnrichmentTriggerException(
                    "force-enrich webhook 호출 실패: jobId=" + jobId + ", policyId=" + policyId, e);
        }
    }
}
