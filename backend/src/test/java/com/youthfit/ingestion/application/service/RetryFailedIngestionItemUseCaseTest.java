package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.dto.result.RetryResult;
import com.youthfit.ingestion.domain.model.FailureReason;
import com.youthfit.ingestion.domain.model.IngestionItemFailure;
import com.youthfit.ingestion.domain.repository.IngestionItemFailureRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RetryFailedIngestionItemUseCaseTest {

    @Test
    void 존재하지_않는_failureId_는_NOT_FOUND() {
        IngestionItemFailureRepository repo = mock(IngestionItemFailureRepository.class);
        when(repo.findById(99L)).thenReturn(Optional.empty());

        RetryFailedIngestionItemUseCase useCase = new RetryFailedIngestionItemUseCase(
                repo, mock(IngestionService.class), new ObjectMapper());

        RetryResult result = useCase.retry(99L);
        assertThat(result.status()).isEqualTo(RetryResult.Status.NOT_FOUND);
    }

    @Test
    void rawPayload_가_null_이면_PAYLOAD_EXPIRED() {
        IngestionItemFailureRepository repo = mock(IngestionItemFailureRepository.class);
        IngestionItemFailure failure = IngestionItemFailure.of(null, "S", "ext", null, FailureReason.OTHER, "x");
        when(repo.findById(1L)).thenReturn(Optional.of(failure));

        RetryFailedIngestionItemUseCase useCase = new RetryFailedIngestionItemUseCase(
                repo, mock(IngestionService.class), new ObjectMapper());

        RetryResult result = useCase.retry(1L);
        assertThat(result.status()).isEqualTo(RetryResult.Status.PAYLOAD_EXPIRED);
    }
}
