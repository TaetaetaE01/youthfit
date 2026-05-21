package com.youthfit.policy.infrastructure.external;

import com.youthfit.policy.application.port.ForceEnrichTrigger;
import com.youthfit.policy.domain.model.PolicyReferenceSite;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class N8nForceEnrichClientTest {

    private static final String WEBHOOK_URL = "http://n8n.test/webhook/force-enrich";
    private static final String API_KEY = "secret-token";

    @Test
    void forceEnrich_은_시크릿_헤더와_payload를_전송한다() {
        // NOTE: MockRestServiceServer.bindTo(RestClient.Builder) mutates the builder
        // to install a mock ClientHttpRequestFactory. The mutation must happen BEFORE
        // the RestClient is built, otherwise the client captures the original factory
        // and makes real network calls. Hence we bind first, then build the client.
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        N8nForceEnrichClient client = new N8nForceEnrichClient(restClient, WEBHOOK_URL, API_KEY);

        server.expect(requestTo(WEBHOOK_URL))
              .andExpect(method(org.springframework.http.HttpMethod.POST))
              .andExpect(header("X-Internal-Api-Key", API_KEY))
              .andExpect(jsonPath("$.jobId").value(100))
              .andExpect(jsonPath("$.policyId").value(42))
              .andExpect(jsonPath("$.urls[0].url").value("https://a.example.com"))
              .andRespond(withSuccess());

        client.forceEnrich(100L, 42L,
                List.of(PolicyReferenceSite.auto("n", "https://a.example.com")));

        server.verify();
    }

    @Test
    void forceEnrich_은_4xx_응답을_EnrichmentTriggerException으로_매핑한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        N8nForceEnrichClient client = new N8nForceEnrichClient(restClient, WEBHOOK_URL, API_KEY);

        server.expect(requestTo(WEBHOOK_URL))
              .andRespond(withBadRequest());

        assertThatThrownBy(() -> client.forceEnrich(100L, 42L,
                List.of(PolicyReferenceSite.auto("n", "https://a.example.com"))))
                .isInstanceOf(ForceEnrichTrigger.EnrichmentTriggerException.class);

        server.verify();
    }
}
