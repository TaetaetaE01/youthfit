package com.youthfit.policy.infrastructure.external;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring 컨텍스트가 N8nForceEnrichClient 빈을 생성자 모호성 없이 인스턴스화하는지 검증한다.
 *
 * <p>회귀 방지: 운영용 생성자와 테스트용 생성자가 공존할 때 @Autowired 힌트가 빠지면
 * Spring 이 어떤 생성자를 써야 할지 결정하지 못해 default constructor 를 찾으려다 실패한다.
 * 외부 인프라(DB/Redis) 없이 이 빈만 로드해 부팅 실패를 단위 수준에서 잡는다.
 */
@SpringJUnitConfig
@Import(N8nForceEnrichClient.class)
@TestPropertySource(properties = {
        "n8n.force-enrich-webhook-url=http://n8n.test/webhook/force-enrich",
        "youthfit.internal.api-key=test-secret"
})
class N8nForceEnrichClientContextTest {

    @Autowired
    private N8nForceEnrichClient client;

    @Test
    void Spring_이_N8nForceEnrichClient_빈을_생성할_수_있다() {
        assertThat(client).isNotNull();
    }
}
