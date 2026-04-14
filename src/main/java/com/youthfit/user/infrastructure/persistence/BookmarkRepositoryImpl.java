package com.youthfit.user.infrastructure.persistence;

import com.youthfit.user.domain.model.Bookmark;
import com.youthfit.user.domain.repository.BookmarkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BookmarkRepositoryImpl implements BookmarkRepository {

    private final BookmarkJpaRepository jpaRepository;

    @Override
    public Bookmark save(Bookmark bookmark) {
        return jpaRepository.save(bookmark);
    }

    @Override
    public Optional<Bookmark> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public boolean existsByUserIdAndPolicyId(Long userId, Long policyId) {
        return jpaRepository.existsByUserIdAndPolicyId(userId, policyId);
    }

    @Override
    public Page<Bookmark> findAllByUserId(Long userId, Pageable pageable) {
        return jpaRepository.findAllByUserId(userId, pageable);
    }

    @Override
    public List<Bookmark> findAllByUserId(Long userId) {
        return jpaRepository.findAllByUserId(userId);
    }

    @Override
    public void delete(Bookmark bookmark) {
        jpaRepository.delete(bookmark);
    }
}
