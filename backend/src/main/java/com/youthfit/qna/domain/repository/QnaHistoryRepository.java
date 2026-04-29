package com.youthfit.qna.domain.repository;

import com.youthfit.qna.domain.model.QnaHistory;

import java.util.Optional;

public interface QnaHistoryRepository {

    QnaHistory save(QnaHistory qnaHistory);

    Optional<QnaHistory> findById(Long id);
}
