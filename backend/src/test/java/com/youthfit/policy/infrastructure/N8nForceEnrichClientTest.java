package com.youthfit.policy.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthfit.policy.domain.model.PolicyReferenceSite;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class N8nForceEnrichClientTest {

    @Test
    void forceEnrich_은_시크릿_헤더와_payload를_전송한다() {
        // NOTE: MockRestServiceServer.bindTo(RestClient.Builder) mutates the builder
        // to install a mock ClientHttpRequestFactory. The mutation must happen BEFORE
        // the RestClient is built, otherwise the client captures the original factory
        // and makes real network calls. Hence we bind first, then construct the client.
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        N8nForceEnrichClient client = new N8nForceEnrichClient(
                builder,
                "http://n8n.test/webhook/force-enrich",
                "secret-token",
                new ObjectMapper()
        );

        server.expect(requestTo("http://n8n.test/webhook/force-enrich"))
              .andExpect(method(org.springframework.http.HttpMethod.POST))
              .andExpect(header("X-Internal-Api-Key", "secret-token"))
              .andExpect(jsonPath("$.jobId").value(100))
              .andExpect(jsonPath("$.policyId").value(42))
              .andExpect(jsonPath("$.urls[0].url").value("https://a.example.com"))
              .andRespond(withSuccess());

        client.forceEnrich(100L, 42L,
                List.of(PolicyReferenceSite.auto("n", "https://a.example.com")));

        server.verify();
    }
}
