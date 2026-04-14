package com.youthfit.qna.infrastructure.persistence;

import com.youthfit.qna.domain.model.QnaHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QnaHistoryJpaRepository extends JpaRepository<QnaHistory, Long> {
}
