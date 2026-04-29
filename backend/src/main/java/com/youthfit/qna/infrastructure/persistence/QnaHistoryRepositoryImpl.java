package com.youthfit.qna.infrastructure.persistence;

import com.youthfit.qna.domain.model.QnaHistory;
import com.youthfit.qna.domain.repository.QnaHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class QnaHistoryRepositoryImpl implements QnaHistoryRepository {

    private final QnaHistoryJpaRepository jpaRepository;

    @Override
    public QnaHistory save(QnaHistory qnaHistory) {
        return jpaRepository.save(qnaHistory);
    }

    @Override
    public Optional<QnaHistory> findById(Long id) {
        return jpaRepository.findById(id);
    }
}
