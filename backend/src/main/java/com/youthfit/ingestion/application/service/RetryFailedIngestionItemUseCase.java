package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.dto.command.IngestPolicyCommand;
import com.youthfit.ingestion.application.dto.result.RetryResult;
import com.youthfit.ingestion.domain.model.IngestionItemFailure;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetryFailedIngestionItemUseCase {

    private final IngestionItemFailureRepository failureRepository;
    private final IngestionService ingestionService;
    private final ObjectMapper objectMapper;

    @Transactional
    public RetryResult retry(Long failureId) {
        IngestionItemFailure failure = failureRepository.findById(failureId).orElse(null);
        if (failure == null) {
            return RetryResult.notFound();
        }
        if (!failure.isPayloadAvailable()) {
            return RetryResult.payloadExpired();
        }

        IngestPolicyCommand command;
        try {
            command = objectMapper.readValue(failure.getRawPayload(), IngestPolicyCommand.class);
        } catch (Exception e) {
            log.warn("재처리 raw_payload 파싱 실패: failureId={}", failureId, e);
            return RetryResult.failure("raw_payload 파싱 실패: " + e.getMessage(), null);
        }

        try {
            ingestionService.receivePolicy(command);
            failure.markRetried();
            failureRepository.save(failure);
            return RetryResult.success();
        } catch (RuntimeException e) {
            failure.markRetried();
            failureRepository.save(failure);
            // ingestionService.receivePolicy 내부 hook 이 새 IngestionItemFailure 적재했을 것 — 그 id 를 추적할 수 있으면 반환
            return RetryResult.failure(e.getMessage(), null);
        }
    }
}
