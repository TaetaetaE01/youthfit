package com.youthfit.user.domain.repository;

import com.youthfit.user.domain.model.EmailSendAttempt;
import com.youthfit.user.domain.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("EmailSendAttemptRepository.search 통합 테스트")
class EmailSendAttemptRepositorySearchTest {

    @Autowired
    private EmailSendAttemptRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("recipient 파라미터가 null 이면 PostgreSQL 타입 오류 없이 전체가 조회된다 (regression)")
    void search_withNullRecipient_returnsAllRows() {
        LocalDateTime now = LocalDateTime.now();
        repository.save(EmailSendAttempt.success(
            1L, 10L, "user@example.com", NotificationType.DEADLINE,
            "ses-1", "테스트 제목", "{}", now));

        Page<EmailSendAttempt> page = repository.search(
            now.minusDays(1), now.plusDays(1),
            null, null, null,
            PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getRecipientEmail()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("recipient 부분 문자열로 LIKE 검색이 동작한다")
    void search_withRecipientSubstring_filtersByEmail() {
        LocalDateTime now = LocalDateTime.now();
        repository.save(EmailSendAttempt.success(
            1L, 10L, "alice@example.com", NotificationType.DEADLINE,
            "ses-1", "제목 A", "{}", now));
        repository.save(EmailSendAttempt.success(
            2L, 11L, "bob@example.com", NotificationType.DEADLINE,
            "ses-2", "제목 B", "{}", now));

        Page<EmailSendAttempt> page = repository.search(
            now.minusDays(1), now.plusDays(1),
            null, null, "alice",
            PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getRecipientEmail()).isEqualTo("alice@example.com");
    }
}
