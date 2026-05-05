package com.youthfit.user.infrastructure.email;

import com.youthfit.user.domain.model.EmailSendAttempt;
import com.youthfit.user.domain.repository.EmailSendAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SesEventHandlerTest {

    @Mock EmailSendAttemptRepository repo;

    SesEventHandler handler;
    Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-05-05T10:00:30Z"), ZoneId.of("UTC"));
        handler = new SesEventHandler(repo, clock);
    }

    @Test
    void delivery_event_markDelivered_호출() {
        EmailSendAttempt attempt = mock(EmailSendAttempt.class);
        when(repo.findBySesMessageId("msg-1")).thenReturn(Optional.of(attempt));

        handler.handle(SesEvent.delivery("msg-1"));

        verify(attempt).markDelivered(LocalDateTime.now(clock));
        verify(repo).save(attempt);
    }

    @Test
    void bounce_event_markBounced_타입_사유_전달() {
        EmailSendAttempt attempt = mock(EmailSendAttempt.class);
        when(repo.findBySesMessageId("msg-2")).thenReturn(Optional.of(attempt));

        handler.handle(SesEvent.bounce("msg-2", "Permanent", "5.1.1 unknown"));

        verify(attempt).markBounced(any(), eq("Permanent"), eq("5.1.1 unknown"));
        verify(repo).save(attempt);
    }

    @Test
    void messageId_매칭_실패시_warn_로그_예외_없음() {
        when(repo.findBySesMessageId("orphan")).thenReturn(Optional.empty());
        handler.handle(SesEvent.delivery("orphan"));
        verify(repo, never()).save(any());
    }
}
