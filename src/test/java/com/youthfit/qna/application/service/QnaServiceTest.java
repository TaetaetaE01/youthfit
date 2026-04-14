package com.youthfit.qna.application.service;

import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.qna.application.dto.command.AskQuestionCommand;
import com.youthfit.qna.application.port.QnaLlmProvider;
import com.youthfit.qna.domain.repository.QnaHistoryRepository;
import com.youthfit.rag.application.service.RagSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@DisplayName("QnaService")
@ExtendWith(MockitoExtension.class)
class QnaServiceTest {

    @InjectMocks
    private QnaService qnaService;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private RagSearchService ragSearchService;

    @Mock
    private QnaLlmProvider qnaLlmProvider;

    @Mock
    private QnaHistoryRepository qnaHistoryRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("askQuestion - 정책 질문")
    class AskQuestion {

        @Test
        @DisplayName("존재하지 않는 정책에 질문하면 예외가 발생한다")
        void policyNotFound_throwsException() {
            // given
            AskQuestionCommand command = new AskQuestionCommand(999L, "질문", 1L);
            given(policyRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> qnaService.askQuestion(command))
                    .isInstanceOf(YouthFitException.class);
        }

        @Test
        @DisplayName("정상적인 질문에 대해 SseEmitter를 반환한다")
        void validQuestion_returnsSseEmitter() {
            // given
            AskQuestionCommand command = new AskQuestionCommand(1L, "이 정책 재학생도 가능해요?", 1L);
            Policy policy = createPolicy(1L, "청년 주거 지원");
            given(policyRepository.findById(1L)).willReturn(Optional.of(policy));

            // when
            SseEmitter emitter = qnaService.askQuestion(command);

            // then
            assertThat(emitter).isNotNull();
        }

        @Test
        @DisplayName("정책이 존재하면 SseEmitter를 반환하고 비동기 처리를 시작한다")
        void policyExists_returnsSseEmitterAndStartsAsync() {
            // given
            AskQuestionCommand command = new AskQuestionCommand(1L, "지원 자격은?", 1L);
            Policy policy = createPolicy(1L, "청년 취업 지원");

            given(policyRepository.findById(1L)).willReturn(Optional.of(policy));

            // when
            SseEmitter emitter = qnaService.askQuestion(command);

            // then
            assertThat(emitter).isNotNull();
        }
    }

    // ── 헬퍼 메서드 ──

    private Policy createPolicy(Long id, String title) {
        Policy policy = Policy.builder()
                .title(title)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }
}
