package com.youthfit.user.infrastructure.scheduler;

import com.youthfit.user.domain.repository.EmailSendAttemptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailSendAttemptCleanupSchedulerTest {

    @Mock EmailSendAttemptRepository repository;

    @Test
    void cleanup_보관기간_이전_row_삭제() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-03T03:30:00Z"), ZoneId.of("UTC"));
        when(repository.deleteOlderThan(any())).thenReturn(15);

        new EmailSendAttemptCleanupScheduler(repository, clock, 90).cleanup();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteOlderThan(captor.capture());
        // 90일 이전 시각: 2026-08-03 - 90 days = 2026-05-05
        assertThat(captor.getValue()).isEqualTo(LocalDateTime.of(2026, 5, 5, 3, 30, 0));
    }
}
