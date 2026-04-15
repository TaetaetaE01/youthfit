package com.youthfit.user.presentation.controller;

import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import com.youthfit.user.application.dto.result.BookmarkResult;
import com.youthfit.user.application.service.BookmarkService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("BookmarkController")
@WebMvcTest(controllers = BookmarkController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class}))
class BookmarkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookmarkService bookmarkService;

    @Test
    @DisplayName("POST /api/v1/bookmarks - 북마크를 생성하면 201을 반환한다")
    void createBookmark_returns201() throws Exception {
        // given
        BookmarkResult result = new BookmarkResult(1L, 100L, LocalDateTime.of(2026, 4, 15, 10, 0));
        given(bookmarkService.createBookmark(eq(1L), any())).willReturn(result);

        // when & then
        mockMvc.perform(post("/api/v1/bookmarks")
                        .with(authentication(createAuth(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"policyId": 100}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bookmarkId").value(1))
                .andExpect(jsonPath("$.data.policyId").value(100));
    }

    @Test
    @DisplayName("POST /api/v1/bookmarks - policyId 없으면 400을 반환한다")
    @WithMockUser
    void createBookmark_missingPolicyId_returns400() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/bookmarks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/v1/bookmarks/{bookmarkId} - 북마크를 삭제하면 200을 반환한다")
    void deleteBookmark_returns200() throws Exception {
        // given
        willDoNothing().given(bookmarkService).deleteBookmark(1L, 10L);

        // when & then
        mockMvc.perform(delete("/api/v1/bookmarks/10")
                        .with(authentication(createAuth(1L)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 반환한다")
    void noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/bookmarks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"policyId": 100}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ── 헬퍼 메서드 ──

    private Authentication createAuth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
