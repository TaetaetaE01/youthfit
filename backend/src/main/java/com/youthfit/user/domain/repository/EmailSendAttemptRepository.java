package com.youthfit.user.domain.repository;

import com.youthfit.user.domain.model.EmailSendAttempt;
import com.youthfit.user.domain.model.EmailSendStatus;
import com.youthfit.user.domain.model.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmailSendAttemptRepository extends JpaRepository<EmailSendAttempt, Long> {

    Optional<EmailSendAttempt> findBySesMessageId(String sesMessageId);

    EmailSendAttempt findTopByOrderByIdDesc();

    @Query("SELECT COUNT(a) FROM EmailSendAttempt a WHERE a.sentAt >= :since")
    long countSentSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM EmailSendAttempt a WHERE a.sentAt >= :since AND a.status IN :failedStatuses")
    long countByStatusSince(@Param("since") LocalDateTime since,
                            @Param("failedStatuses") Collection<EmailSendStatus> failedStatuses);

    @Query("""
        SELECT a FROM EmailSendAttempt a
        WHERE a.sentAt BETWEEN :from AND :to
          AND (:#{#statuses == null || #statuses.isEmpty()} = true OR a.status IN :statuses)
          AND (:emailType IS NULL OR a.emailType = :emailType)
          AND (CAST(:recipient AS string) IS NULL OR a.recipientEmail LIKE CONCAT('%', CAST(:recipient AS string), '%'))
        ORDER BY a.sentAt DESC
        """)
    Page<EmailSendAttempt> search(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        @Param("statuses") List<EmailSendStatus> statuses,
        @Param("emailType") NotificationType emailType,
        @Param("recipient") String recipient,
        Pageable pageable);

    @Query(value = """
        SELECT to_char(date_trunc('day', sent_at), 'YYYY-MM-DD') AS day,
               status,
               COUNT(*) AS cnt
        FROM email_send_attempt
        WHERE sent_at BETWEEN :from AND :to
        GROUP BY day, status
        ORDER BY day
        """, nativeQuery = true)
    List<Object[]> aggregateDaily(@Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);

    @Modifying
    @Query("DELETE FROM EmailSendAttempt a WHERE a.sentAt < :threshold")
    int deleteOlderThan(@Param("threshold") LocalDateTime threshold);
}
