package com.youthfit.guide.infrastructure.external;

import com.youthfit.guide.domain.model.GuideContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OpenAiChatClientTest {

    @Mock OpenAiChatProperties properties;

    @Test
    void parseResponse_단일그룹_라벨없는_paired_파싱() {
        OpenAiChatClient client = new OpenAiChatClient(properties);
        String json = """
                {
                  "oneLineSummary": "만 19~34세 청년 월세 지원",
                  "target": {
                    "groups": [
                      { "label": null, "items": ["만 19~34세", "본인 명의 계약자"] }
                    ]
                  },
                  "criteria": null,
                  "content": null,
                  "pitfalls": [
                    { "text": "월세 60만원 초과 제외", "sourceField": "SUPPORT_TARGET" }
                  ]
                }
                """;
        GuideContent content = client.parseResponse(json);
        assertThat(content.oneLineSummary()).isEqualTo("만 19~34세 청년 월세 지원");
        assertThat(content.target().groups()).hasSize(1);
        assertThat(content.target().groups().get(0).label()).isNull();
        assertThat(content.target().groups().get(0).items()).containsExactly("만 19~34세", "본인 명의 계약자");
        assertThat(content.criteria()).isNull();
        assertThat(content.pitfalls()).hasSize(1);
        assertThat(content.pitfalls().get(0).text()).isEqualTo("월세 60만원 초과 제외");
    }

    @Test
    void buildResponseFormat_includesAttachmentEnumAndAttachmentRef() throws Exception {
        OpenAiChatClient client = new OpenAiChatClient(properties);
        Method m = OpenAiChatClient.class.getDeclaredMethod("buildResponseFormat");
        m.setAccessible(true);
        Object format = m.invoke(client);
        String json = new ObjectMapper().writeValueAsString(format);

        assertThat(json).contains("ATTACHMENT");
        assertThat(json).contains("attachmentRef");
        assertThat(json).contains("attachmentId");
        assertThat(json).contains("pageStart");
        assertThat(json).contains("pageEnd");
    }

    @Test
    void parseResponse_라벨있는_여러그룹_paired_파싱() {
        OpenAiChatClient client = new OpenAiChatClient(properties);
        String json = """
                {
                  "oneLineSummary": "공공분양",
                  "target": null,
                  "criteria": {
                    "groups": [
                      { "label": "일반공급 - 소득 기준", "items": ["a", "b"] },
                      { "label": "특별공급 - 소득 기준", "items": ["c"] }
                    ]
                  },
                  "content": null,
                  "pitfalls": []
                }
                """;
        GuideContent content = client.parseResponse(json);
        assertThat(content.criteria().groups()).hasSize(2);
        assertThat(content.criteria().groups().get(0).label()).isEqualTo("일반공급 - 소득 기준");
        assertThat(content.criteria().groups().get(0).items()).containsExactly("a", "b");
        assertThat(content.criteria().groups().get(1).label()).isEqualTo("특별공급 - 소득 기준");
    }
}
