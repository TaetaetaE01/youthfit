package com.youthfit.qna.presentation.controller;

import com.youthfit.qna.application.service.QnaService;
import com.youthfit.qna.presentation.dto.request.AskQuestionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class QnaController implements QnaApi {

    private final QnaService qnaService;

    @PostMapping(value = "/api/v1/qna/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Override
    public SseEmitter askQuestion(
            @Valid @RequestBody AskQuestionRequest request,
            @AuthenticationPrincipal Long userId) {

        return qnaService.askQuestion(request.toCommand(userId));
    }
}
