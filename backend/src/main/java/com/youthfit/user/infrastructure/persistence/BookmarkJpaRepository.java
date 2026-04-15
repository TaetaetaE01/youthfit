package com.youthfit.user.infrastructure.persistence;

import com.youthfit.user.domain.model.Bookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookmarkJpaRepository extends JpaRepository<Bookmark, Long> {

    boolean existsByUserIdAndPolicyId(Long userId, Long policyId);

    Page<Bookmark> findAllByUserId(Long userId, Pageable pageable);

    List<Bookmark> findAllByUserId(Long userId);
}
