package com.youthfit.user.domain.repository;

import com.youthfit.user.domain.model.Bookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository {

    Bookmark save(Bookmark bookmark);

    Optional<Bookmark> findById(Long id);

    boolean existsByUserIdAndPolicyId(Long userId, Long policyId);

    Page<Bookmark> findAllByUserId(Long userId, Pageable pageable);

    List<Bookmark> findAllByUserId(Long userId);

    void delete(Bookmark bookmark);
}
