package com.youthfit.user.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.application.dto.command.CreateBookmarkCommand;
import com.youthfit.user.application.dto.result.BookmarkResult;
import com.youthfit.user.application.dto.result.BookmarkWithPolicyResult;
import com.youthfit.user.domain.model.Bookmark;
import com.youthfit.user.domain.repository.BookmarkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final PolicyRepository policyRepository;

    @Transactional
    public BookmarkResult createBookmark(Long userId, CreateBookmarkCommand command) {
        policyRepository.findById(command.policyId())
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "정책을 찾을 수 없습니다"));

        if (bookmarkRepository.existsByUserIdAndPolicyId(userId, command.policyId())) {
            throw new YouthFitException(ErrorCode.DUPLICATE, "이미 북마크한 정책입니다");
        }

        Bookmark bookmark = new Bookmark(userId, command.policyId());
        Bookmark saved = bookmarkRepository.save(bookmark);
        return BookmarkResult.from(saved);
    }

    @Transactional
    public void deleteBookmark(Long userId, Long bookmarkId) {
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "북마크를 찾을 수 없습니다"));

        if (!bookmark.getUserId().equals(userId)) {
            throw new YouthFitException(ErrorCode.FORBIDDEN, "본인의 북마크만 삭제할 수 있습니다");
        }

        bookmarkRepository.delete(bookmark);
    }

    @Transactional(readOnly = true)
    public Page<BookmarkWithPolicyResult> findMyBookmarks(Long userId, Pageable pageable) {
        Page<Bookmark> bookmarks = bookmarkRepository.findAllByUserId(userId, pageable);

        return bookmarks.map(bookmark -> {
            Policy policy = policyRepository.findById(bookmark.getPolicyId())
                    .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "정책을 찾을 수 없습니다"));
            return BookmarkWithPolicyResult.of(bookmark, policy);
        });
    }
}
