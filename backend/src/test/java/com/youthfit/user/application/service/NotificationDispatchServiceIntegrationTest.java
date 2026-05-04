package com.youthfit.user.application.service;

import com.youthfit.user.domain.model.NotificationHistory;
import com.youthfit.user.domain.model.NotificationStatus;
import com.youthfit.user.domain.model.NotificationType;
import com.youthfit.user.domain.repository.NotificationHistoryRepository;
import com.youthfit.user.infrastructure.persistence.NotificationHistoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationDispatchService 통합 테스트")
@SpringBootTest
class NotificationDispatchServiceIntegrationTest {

    @Autowired
    private NotificationDispatchService dispatchService;

    @Autowired
    private NotificationHistoryJpaRepository jpaRepository;

    @Autowired
    private NotificationHistoryRepository repository;

    @BeforeEach
    void cleanUp() {
        jpaRepository.deleteAll();
    }

    @Test
    @DisplayName("reservePending 은 PENDING 행을 별도 트랜잭션으로 commit 한다")
    void reservePending_commitsInSeparateTransaction() {
        // when
        NotificationHistory history = dispatchService.reservePending(1L, 100L, NotificationType.DEADLINE);

        // then
        assertThat(history).isNotNull();
        assertThat(history.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(jpaRepository.findById(history.getId())).isPresent();
    }

    @Test
    @DisplayName("이미 동일 (user, policy, type) 행이 있으면 reservePending 은 null 반환")
    void reservePending_existingRow_returnsNull() {
        // given
        repository.save(NotificationHistory.pending(1L, 100L, NotificationType.DEADLINE));

        // when
        NotificationHistory result = dispatchService.reservePending(1L, 100L, NotificationType.DEADLINE);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("markSent 는 PENDING → SENT 로 전이한다")
    void markSent_transitionsToSent() {
        // given
        NotificationHistory pending = dispatchService.reservePending(1L, 100L, NotificationType.DEADLINE);

        // when
        dispatchService.markSent(pending.getId());

        // then
        Optional<NotificationHistory> found = jpaRepository.findById(pending.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(found.get().getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed 는 PENDING → FAILED 로 전이하고 failureReason 저장")
    void markFailed_transitionsToFailed() {
        // given
        NotificationHistory pending = dispatchService.reservePending(1L, 100L, NotificationType.DEADLINE);

        // when
        dispatchService.markFailed(pending.getId(), "SES 호출 실패");

        // then
        Optional<NotificationHistory> found = jpaRepository.findById(pending.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(found.get().getFailedAt()).isNotNull();
        assertThat(found.get().getFailureReason()).isEqualTo("SES 호출 실패");
    }
}
