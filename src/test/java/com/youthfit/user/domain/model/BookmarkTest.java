package com.youthfit.user.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Bookmark Entity")
class BookmarkTest {

    @Test
    @DisplayName("북마크 생성 시 userId와 policyId가 설정된다")
    void create_setsUserIdAndPolicyId() {
        // given & when
        Bookmark bookmark = new Bookmark(1L, 100L);

        // then
        assertThat(bookmark.getUserId()).isEqualTo(1L);
        assertThat(bookmark.getPolicyId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("생성 직후 id는 null이다")
    void create_idIsNull() {
        // given & when
        Bookmark bookmark = new Bookmark(1L, 100L);

        // then
        assertThat(bookmark.getId()).isNull();
    }
}
