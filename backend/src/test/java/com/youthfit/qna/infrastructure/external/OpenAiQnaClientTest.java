package com.youthfit.qna.infrastructure.external;

import com.youthfit.qna.application.dto.command.PolicyMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenAiQnaClient.buildUserMessage")
class OpenAiQnaClientTest {

    @Nested
    @DisplayName("9필드 모두 채워진 메타데이터")
    class FullMetadata {

        @Test
        @DisplayName("정책명·메타 9라인·본문 컨텍스트·질문이 모두 포함된다")
        void allFieldsRendered() {
            PolicyMetadata metadata = new PolicyMetadata(
                    "WELFARE",
                    "저소득 청년 자산형성 지원",
                    "만 19~34세, 근로소득자",
                    "월 30만원 매칭",
                    "보건복지부",
                    "02-123-4567",
                    LocalDate.of(2026, 5, 1),
                    LocalDate.of(2026, 5, 31),
                    "현금"
            );
            String context = "[청크 0]\n본문 내용\n\n";
            String question = "재학생도 가능?";

            String userMessage = OpenAiQnaClient.buildUserMessage("청년내일저축계좌", metadata, context, question);

            assertThat(userMessage).contains("정책명: 청년내일저축계좌");
            assertThat(userMessage).contains("정책 메타데이터:");
            assertThat(userMessage).contains("- 분야: WELFARE");
            assertThat(userMessage).contains("- 요약: 저소득 청년 자산형성 지원");
            assertThat(userMessage).contains("- 지원 대상: 만 19~34세, 근로소득자");
            assertThat(userMessage).contains("- 지원 내용: 월 30만원 매칭");
            assertThat(userMessage).contains("- 운영 기관: 보건복지부");
            assertThat(userMessage).contains("- 문의처: 02-123-4567");
            assertThat(userMessage).contains("- 신청 기간: 2026-05-01 ~ 2026-05-31");
            assertThat(userMessage).contains("- 지급 방식: 현금");
            assertThat(userMessage).contains("정책 본문 컨텍스트:\n[청크 0]\n본문 내용");
            assertThat(userMessage).contains("질문: 재학생도 가능?");
        }
    }

    @Nested
    @DisplayName("null 필드 처리")
    class NullFields {

        @Test
        @DisplayName("category 만 채워진 메타데이터는 다른 라인이 생략된다")
        void onlyCategoryRendered() {
            PolicyMetadata metadata = new PolicyMetadata(
                    "JOBS", null, null, null, null, null, null, null, null
            );

            String userMessage = OpenAiQnaClient.buildUserMessage("정책", metadata, "context", "질문");

            assertThat(userMessage).contains("- 분야: JOBS");
            assertThat(userMessage).doesNotContain("- 요약:");
            assertThat(userMessage).doesNotContain("- 지원 대상:");
            assertThat(userMessage).doesNotContain("- 지원 내용:");
            assertThat(userMessage).doesNotContain("- 운영 기관:");
            assertThat(userMessage).doesNotContain("- 문의처:");
            assertThat(userMessage).doesNotContain("- 신청 기간:");
            assertThat(userMessage).doesNotContain("- 지급 방식:");
        }

        @Test
        @DisplayName("applyStart 만 있고 applyEnd 가 null 이면 신청 기간 라인 생략")
        void partialApplyDates_skipsLine() {
            PolicyMetadata metadata = new PolicyMetadata(
                    null, null, null, null, null, null,
                    LocalDate.of(2026, 5, 1), null, null
            );

            String userMessage = OpenAiQnaClient.buildUserMessage("정책", metadata, "context", "질문");

            assertThat(userMessage).doesNotContain("- 신청 기간:");
        }

        @Test
        @DisplayName("모든 메타 필드가 null 이면 메타 블록 헤더만 남기지 않는다")
        void allNull_skipsBlock() {
            PolicyMetadata metadata = new PolicyMetadata(
                    null, null, null, null, null, null, null, null, null
            );

            String userMessage = OpenAiQnaClient.buildUserMessage("정책", metadata, "context", "질문");

            assertThat(userMessage).doesNotContain("정책 메타데이터:");
            assertThat(userMessage).contains("정책명: 정책");
            assertThat(userMessage).contains("정책 본문 컨텍스트:");
            assertThat(userMessage).contains("질문: 질문");
        }
    }
}
