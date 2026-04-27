package com.youthfit.guide.infrastructure.external;

import com.youthfit.guide.domain.model.GuideContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OpenAiChatClientTest {

    @Mock OpenAiChatProperties properties;

    @Test
    void parseResponse_정상_JSON을_파싱() {
        OpenAiChatClient client = new OpenAiChatClient(properties);
        String json = """
                {
                  "oneLineSummary": "만 19~34세 청년 월세 지원",
                  "target": { "items": ["만 19~34세", "본인 명의 계약자"] },
                  "criteria": null,
                  "content": null,
                  "pitfalls": [
                    { "text": "월세 60만원 초과 제외", "sourceField": "SUPPORT_TARGET" }
                  ]
                }
                """;
        GuideContent content = client.parseResponse(json);
        assertThat(content.oneLineSummary()).isEqualTo("만 19~34세 청년 월세 지원");
        assertThat(content.target().items()).containsExactly("만 19~34세", "본인 명의 계약자");
        assertThat(content.criteria()).isNull();
        assertThat(content.pitfalls()).hasSize(1);
        assertThat(content.pitfalls().get(0).text()).isEqualTo("월세 60만원 초과 제외");
    }
}
