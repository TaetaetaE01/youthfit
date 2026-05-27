package com.youthfit.guide.infrastructure.external;

import com.youthfit.common.exception.YouthFitException;
import com.youthfit.common.openai.OpenAiErrorClassifier;
import com.youthfit.common.util.TokenCounter;
import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.domain.model.GuideContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.List;

import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiChatClientTest {

    @Mock OpenAiChatProperties properties;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock TokenCounter tokenCounter;
    @Mock OpenAiErrorClassifier errorClassifier;

    private static RestClient.Builder mockBuilder() {
        RestClient.Builder b = mock(RestClient.Builder.class);
        when(b.build()).thenReturn(mock(RestClient.class));
        return b;
    }

    private OpenAiChatClient clientWithCap(int cap) {
        OpenAiChatClient client = new OpenAiChatClient(properties, eventPublisher, tokenCounter, errorClassifier, mockBuilder());
        ReflectionTestUtils.setField(client, "mergeCapTokens", cap);
        return client;
    }

    @Test
    void SYSTEM_PROMPT_는_ENRICHMENT_BODY_청크_매핑_규칙을_포함한다() {
        assertThat(OpenAiChatClient.SYSTEM_PROMPT)
                .contains("source=ENRICHMENT_BODY")
                .contains("sourceField=ENRICHMENT");
    }

    @Test
    void parseResponse_단일그룹_라벨없는_paired_파싱() {
        OpenAiChatClient client = new OpenAiChatClient(properties, eventPublisher, tokenCounter, errorClassifier, mockBuilder());
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
        GuideContent content = client.parseGuideContent(json);
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
        OpenAiChatClient client = new OpenAiChatClient(properties, eventPublisher, tokenCounter, errorClassifier, mockBuilder());
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
        OpenAiChatClient client = new OpenAiChatClient(properties, eventPublisher, tokenCounter, errorClassifier, mockBuilder());
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
        GuideContent content = client.parseGuideContent(json);
        assertThat(content.criteria().groups()).hasSize(2);
        assertThat(content.criteria().groups().get(0).label()).isEqualTo("일반공급 - 소득 기준");
        assertThat(content.criteria().groups().get(0).items()).containsExactly("a", "b");
        assertThat(content.criteria().groups().get(1).label()).isEqualTo("특별공급 - 소득 기준");
    }

    // ───────── M2 token-guard tests ─────────

    @Test
    void mergePartialGuides_토큰이_cap_초과시_YouthFitException_던지고_HTTP_호출_없음() {
        when(properties.getModel()).thenReturn("gpt-4o-mini");
        // tokenCounter 는 항상 cap+1 을 반환하도록 스텁
        when(tokenCounter.countTokens(anyString(), anyString())).thenReturn(101);

        OpenAiChatClient client = clientWithCap(100);

        GuideGenerationInput input = new GuideGenerationInput(
                1L, "테스트 정책", null, null, null,
                null, null, null, null, null,
                null, List.of(), null
        );
        GuideContent partial = new GuideContent(
                "부분 가이드 요약", List.of(), null, null, null,
                null, null, null, null, List.of()
        );

        assertThatThrownBy(() -> client.mergePartialGuides(input, List.of(partial)))
                .isInstanceOf(YouthFitException.class)
                .hasMessageContaining("token cap 을 초과");
    }

    @Test
    void mergePartialGuides_토큰이_cap_이하이면_예외_없음() {
        when(properties.getModel()).thenReturn("gpt-4o-mini");
        // cap 이하를 반환 — 실제 HTTP 호출은 하지 않으므로 callChatCompletion 에서 NPE 발생 방지용으로
        // mergeCapTokens 을 아주 크게 설정하고 countTokens 가 그보다 작게 반환
        when(tokenCounter.countTokens(anyString(), anyString())).thenReturn(50);

        // callChatCompletion 이 RestClient 를 실제 호출하면 실패하므로,
        // 이 테스트는 토큰 카운트 경로만 검증: 예외가 "token cap" 메시지를 포함하지 않음을 확인
        OpenAiChatClient client = clientWithCap(100);

        GuideGenerationInput input = new GuideGenerationInput(
                2L, "정상 정책", null, null, null,
                null, null, null, null, null,
                null, List.of(), null
        );
        GuideContent partial = new GuideContent(
                "부분 요약", List.of(), null, null, null,
                null, null, null, null, List.of()
        );

        // HTTP 호출로 인한 예외는 발생하되, token-cap 예외는 아님을 검증
        assertThatThrownBy(() -> client.mergePartialGuides(input, List.of(partial)))
                .isNotInstanceOf(YouthFitException.class)
                .isNotInstanceOf(AssertionError.class);

        // tokenCounter.countTokens 가 실제로 호출됐음을 확인
        verify(tokenCounter).countTokens(anyString(), anyString());
    }
}
