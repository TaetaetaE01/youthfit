package com.youthfit.qna.presentation.controller;

import com.youthfit.qna.presentation.dto.request.AskQuestionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "정책 Q&A", description = "정책에 대한 자연어 질의응답 API (SSE 스트리밍)")
public interface QnaApi {

    @Operation(
            summary = "정책 질문",
            description = "특정 정책에 대해 자연어로 질문하면 인덱싱된 원문을 근거로 스트리밍 답변을 생성한다. "
                    + "응답은 SSE(Server-Sent Events) 형식으로 CHUNK, SOURCES, DONE 이벤트를 순차 전송한다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "스트리밍 답변 시작"),
            @ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)"),
            @ApiResponse(responseCode = "401", description = "인증이 필요합니다 (YF-002)"),
            @ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없습니다 (YF-004)"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    SseEmitter askQuestion(AskQuestionRequest request, @AuthenticationPrincipal Long userId);
}
