package com.youthfit.auth.infrastructure.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@DisplayName("JwtAuthenticationFilter")
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private FilterChain filterChain;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("유효한 토큰이 있는 경우")
    class ValidToken {

        @Test
        @DisplayName("SecurityContext에 인증 정보를 설정한다")
        void setsAuthentication() throws ServletException, IOException {
            // given
            request.addHeader("Authorization", "Bearer valid-token");
            given(jwtProvider.isValid("valid-token")).willReturn(true);
            given(jwtProvider.extractUserId("valid-token")).willReturn(1L);
            given(jwtProvider.extractRole("valid-token")).willReturn("USER");

            // when
            filter.doFilterInternal(request, response, filterChain);

            // then
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getPrincipal()).isEqualTo(1L);
            assertThat(auth.getAuthorities())
                    .extracting("authority")
                    .containsExactly("ROLE_USER");
            then(filterChain).should().doFilter(request, response);
        }

        @Test
        @DisplayName("ADMIN 역할도 올바르게 설정한다")
        void setsAdminRole() throws ServletException, IOException {
            // given
            request.addHeader("Authorization", "Bearer admin-token");
            given(jwtProvider.isValid("admin-token")).willReturn(true);
            given(jwtProvider.extractUserId("admin-token")).willReturn(2L);
            given(jwtProvider.extractRole("admin-token")).willReturn("ADMIN");

            // when
            filter.doFilterInternal(request, response, filterChain);

            // then
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getAuthorities())
                    .extracting("authority")
                    .containsExactly("ROLE_ADMIN");
        }
    }

    @Nested
    @DisplayName("토큰이 없는 경우")
    class NoToken {

        @Test
        @DisplayName("Authorization 헤더가 없으면 인증을 설정하지 않는다")
        void noHeader_noAuthentication() throws ServletException, IOException {
            // when
            filter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            then(filterChain).should().doFilter(request, response);
        }

        @Test
        @DisplayName("Bearer 접두사가 없으면 인증을 설정하지 않는다")
        void noBearerPrefix_noAuthentication() throws ServletException, IOException {
            // given
            request.addHeader("Authorization", "Basic some-credentials");

            // when
            filter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            then(filterChain).should().doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("유효하지 않은 토큰인 경우")
    class InvalidToken {

        @Test
        @DisplayName("유효하지 않은 토큰이면 인증을 설정하지 않고 필터 체인을 계속한다")
        void invalidToken_noAuthentication() throws ServletException, IOException {
            // given
            request.addHeader("Authorization", "Bearer invalid-token");
            given(jwtProvider.isValid("invalid-token")).willReturn(false);

            // when
            filter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            then(jwtProvider).should(never()).extractUserId("invalid-token");
            then(filterChain).should().doFilter(request, response);
        }
    }
}
