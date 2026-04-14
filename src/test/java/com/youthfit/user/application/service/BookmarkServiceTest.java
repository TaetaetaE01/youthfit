package com.youthfit.user.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.application.dto.command.CreateBookmarkCommand;
import com.youthfit.user.application.dto.result.BookmarkResult;
import com.youthfit.user.application.dto.result.BookmarkWithPolicyResult;
import com.youthfit.user.domain.model.Bookmark;
import com.youthfit.user.domain.repository.BookmarkRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@DisplayName("BookmarkService")
@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @InjectMocks
    private BookmarkService bookmarkService;

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Nested
    @DisplayName("createBookmark - 북마크 추가")
    class CreateBookmark {

        @Test
        @DisplayName("정상적으로 북마크를 추가한다")
        void success_returnsBookmarkResult() {
            // given
            Policy policy = createMockPolicy();
            given(policyRepository.findById(1L)).willReturn(Optional.of(policy));
            given(bookmarkRepository.existsByUserIdAndPolicyId(1L, 1L)).willReturn(false);

            Bookmark saved = createMockBookmark(10L, 1L, 1L);
            given(bookmarkRepository.save(any(Bookmark.class))).willReturn(saved);

            // when
            BookmarkResult result = bookmarkService.createBookmark(1L, new CreateBookmarkCommand(1L));

            // then
            assertThat(result.bookmarkId()).isEqualTo(10L);
            assertThat(result.policyId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("존재하지 않는 정책에 북마크하면 NOT_FOUND 예외가 발생한다")
        void policyNotFound_throwsException() {
            // given
            given(policyRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> bookmarkService.createBookmark(1L, new CreateBookmarkCommand(999L)))
                    .isInstanceOf(YouthFitException.class)
                    .satisfies(ex -> {
                        YouthFitException yfe = (YouthFitException) ex;
                        assertThat(yfe.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    });
        }

        @Test
        @DisplayName("이미 북마크한 정책이면 DUPLICATE 예외가 발생한다")
        void duplicate_throwsException() {
            // given
            Policy policy = createMockPolicy();
            given(policyRepository.findById(1L)).willReturn(Optional.of(policy));
            given(bookmarkRepository.existsByUserIdAndPolicyId(1L, 1L)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> bookmarkService.createBookmark(1L, new CreateBookmarkCommand(1L)))
                    .isInstanceOf(YouthFitException.class)
                    .satisfies(ex -> {
                        YouthFitException yfe = (YouthFitException) ex;
                        assertThat(yfe.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE);
                    });
        }
    }

    @Nested
    @DisplayName("deleteBookmark - 북마크 삭제")
    class DeleteBookmark {

        @Test
        @DisplayName("본인의 북마크를 삭제한다")
        void success_deletesBookmark() {
            // given
            Bookmark bookmark = createMockBookmark(10L, 1L, 1L);
            given(bookmarkRepository.findById(10L)).willReturn(Optional.of(bookmark));

            // when
            bookmarkService.deleteBookmark(1L, 10L);

            // then
            then(bookmarkRepository).should().delete(bookmark);
        }

        @Test
        @DisplayName("존재하지 않는 북마크를 삭제하면 NOT_FOUND 예외가 발생한다")
        void notFound_throwsException() {
            // given
            given(bookmarkRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> bookmarkService.deleteBookmark(1L, 999L))
                    .isInstanceOf(YouthFitException.class)
                    .satisfies(ex -> {
                        YouthFitException yfe = (YouthFitException) ex;
                        assertThat(yfe.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    });
        }

        @Test
        @DisplayName("다른 사용자의 북마크를 삭제하면 FORBIDDEN 예외가 발생한다")
        void otherUser_throwsForbiddenException() {
            // given
            Bookmark bookmark = createMockBookmark(10L, 2L, 1L);
            given(bookmarkRepository.findById(10L)).willReturn(Optional.of(bookmark));

            // when & then
            assertThatThrownBy(() -> bookmarkService.deleteBookmark(1L, 10L))
                    .isInstanceOf(YouthFitException.class)
                    .satisfies(ex -> {
                        YouthFitException yfe = (YouthFitException) ex;
                        assertThat(yfe.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    });
        }
    }

    @Nested
    @DisplayName("findMyBookmarks - 내 북마크 목록 조회")
    class FindMyBookmarks {

        @Test
        @DisplayName("북마크 목록을 정책 요약 정보와 함께 조회한다")
        void success_returnsPageWithPolicySummary() {
            // given
            Bookmark bookmark = createMockBookmark(10L, 1L, 1L);
            Pageable pageable = PageRequest.of(0, 20);
            Page<Bookmark> bookmarkPage = new PageImpl<>(List.of(bookmark), pageable, 1);
            given(bookmarkRepository.findAllByUserId(1L, pageable)).willReturn(bookmarkPage);

            Policy policy = createMockPolicy();
            given(policyRepository.findById(1L)).willReturn(Optional.of(policy));

            // when
            Page<BookmarkWithPolicyResult> result = bookmarkService.findMyBookmarks(1L, pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
            BookmarkWithPolicyResult item = result.getContent().get(0);
            assertThat(item.bookmarkId()).isEqualTo(10L);
            assertThat(item.policy().title()).isEqualTo("2026 청년월세지원");
            assertThat(item.policy().category()).isEqualTo("HOUSING");
        }

        @Test
        @DisplayName("북마크가 없으면 빈 페이지를 반환한다")
        void empty_returnsEmptyPage() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            Page<Bookmark> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            given(bookmarkRepository.findAllByUserId(1L, pageable)).willReturn(emptyPage);

            // when
            Page<BookmarkWithPolicyResult> result = bookmarkService.findMyBookmarks(1L, pageable);

            // then
            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getContent()).isEmpty();
        }
    }

    // ── 헬퍼 메서드 ──

    private Bookmark createMockBookmark(Long id, Long userId, Long policyId) {
        Bookmark bookmark = new Bookmark(userId, policyId);
        ReflectionTestUtils.setField(bookmark, "id", id);
        ReflectionTestUtils.setField(bookmark, "createdAt", LocalDateTime.of(2026, 4, 13, 14, 0));
        return bookmark;
    }

    private Policy createMockPolicy() {
        Policy policy = Policy.builder()
                .title("2026 청년월세지원")
                .summary("월세 지원 정책")
                .category(Category.HOUSING)
                .regionCode("11")
                .applyStart(LocalDate.of(2026, 4, 1))
                .applyEnd(LocalDate.of(2026, 5, 31))
                .build();
        ReflectionTestUtils.setField(policy, "id", 1L);
        return policy;
    }
}
