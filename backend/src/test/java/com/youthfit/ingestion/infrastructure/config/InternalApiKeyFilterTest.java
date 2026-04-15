package com.youthfit.ingestion.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@DisplayName("InternalApiKeyFilter")
@ExtendWith(MockitoExtension.class)
class InternalApiKeyFilterTest {

    private InternalApiKeyFilter filter;

    @Mock
    private FilterChain filterChain;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private static final String VALID_API_KEY = "test-internal-api-key";

    @BeforeEach
    void setUp() {
        InternalApiKeyProperties properties = new InternalApiKeyProperties(VALID_API_KEY);
        filter = new InternalApiKeyFilter(properties);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Nested
    @DisplayName("shouldNotFilter - 필터 적용 대상 판단")
    class ShouldNotFilter {

        @Test
        @DisplayName("내부 API 경로가 아니면 필터를 적용하지 않는다")
        void nonInternalPath_shouldNotFilter() {
            // given
            request.setRequestURI("/api/v1/policies");

            // when & then
            assertThat(filter.shouldNotFilter(request)).isTrue();
        }

        @Test
        @DisplayName("내부 API 경로이면 필터를 적용한다")
        void internalPath_shouldFilter() {
            // given
            request.setRequestURI("/api/internal/ingestion");

            // when & then
            assertThat(filter.shouldNotFilter(request)).isFalse();
        }
    }

    @Nested
    @DisplayName("doFilterInternal - 내부 API 키 검증")
    class DoFilterInternal {

        @Test
        @DisplayName("올바른 API 키로 요청하면 필터 체인을 계속한다")
        void validApiKey_continuesChain() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/internal/ingestion");
            request.addHeader("X-Internal-Api-Key", VALID_API_KEY);

            // when
            filter.doFilterInternal(request, response, filterChain);

            // then
            then(filterChain).should().doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("API 키가 없으면 401을 반환한다")
        void missingApiKey_returns401() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/internal/ingestion");

            // when
            filter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("YF-002");
            then(filterChain).should(never()).doFilter(request, response);
        }

        @Test
        @DisplayName("잘못된 API 키이면 401을 반환한다")
        void wrongApiKey_returns401() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/internal/ingestion");
            request.addHeader("X-Internal-Api-Key", "wrong-key");

            // when
            filter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("유효하지 않은 내부 API 키입니다");
            then(filterChain).should(never()).doFilter(request, response);
        }

        @Test
        @DisplayName("서버에 API 키가 설정되지 않으면 401을 반환한다")
        void blankServerKey_returns401() throws ServletException, IOException {
            // given
            InternalApiKeyProperties emptyProps = new InternalApiKeyProperties("");
            InternalApiKeyFilter emptyFilter = new InternalApiKeyFilter(emptyProps);
            request.setRequestURI("/api/internal/ingestion");
            request.addHeader("X-Internal-Api-Key", "any-key");

            // when
            emptyFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getStatus()).isEqualTo(401);
            then(filterChain).should(never()).doFilter(request, response);
        }

        @Test
        @DisplayName("서버에 API 키가 null이면 401을 반환한다")
        void nullServerKey_returns401() throws ServletException, IOException {
            // given
            InternalApiKeyProperties nullProps = new InternalApiKeyProperties(null);
            InternalApiKeyFilter nullFilter = new InternalApiKeyFilter(nullProps);
            request.setRequestURI("/api/internal/ingestion");
            request.addHeader("X-Internal-Api-Key", "any-key");

            // when
            nullFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getStatus()).isEqualTo(401);
            then(filterChain).should(never()).doFilter(request, response);
        }
    }
}
