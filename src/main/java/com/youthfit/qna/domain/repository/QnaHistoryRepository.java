package com.youthfit.qna.domain.repository;

import com.youthfit.qna.domain.model.QnaHistory;

public interface QnaHistoryRepository {

    QnaHistory save(QnaHistory qnaHistory);
}
