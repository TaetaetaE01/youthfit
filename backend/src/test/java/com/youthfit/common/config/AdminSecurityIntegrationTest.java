package com.youthfit.common.config;

import com.youthfit.admin.presentation.controller.AdminPingController;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationEntryPoint;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import com.youthfit.user.application.service.UserProfileService;
import com.youthfit.user.presentation.controller.UserProfileController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 보안 통합 테스트.
 *
 * <p>본 프로젝트는 임베디드 DB 인프라가 없어 {@code @SpringBootTest} 가
 * PostgreSQL 연결을 시도하다 실패한다(예: {@link com.youthfit.eligibility.presentation.controller.EligibilityControllerIntegrationTest}).
 * 따라서 {@code @WebMvcTest} 슬라이스로 작성하되, 실제 {@link SecurityConfig}와
 * {@link JwtAuthenticationEntryPoint} 를 함께 import 하여 SecurityFilterChain
 * + RoleHierarchy 빈이 포함된 보안 컨텍스트를 검증한다.
 */
@WebMvcTest(controllers = {AdminPingController.class, UserProfileController.class})
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
@DisplayName("어드민 보안 통합")
class AdminSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // SecurityConfig 가 주입받는 필터들 — 슬라이스에선 실제 빈이 없으므로 Mockito 빈으로 대체
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private InternalApiKeyFilter internalApiKeyFilter;

    // UserProfileController 가 의존하는 서비스 — me 엔드포인트 응답 흐름은 검증 대상이 아님
    @MockitoBean
    private UserProfileService userProfileService;

    // SecurityConfig 에 정의된 RoleHierarchy 빈 — 역할 상속을 빈 단위로 검증
    @Autowired
    private RoleHierarchy roleHierarchy;

    /**
     * Mockito 가 생성한 필터는 기본적으로 {@code doFilter} 가 no-op 이라 체인이 끊긴다.
     * 인증 필터 책임은 검증 대상이 아니므로, 단순 통과(pass-through) 로 스텁한다.
     */
    @BeforeEach
    void stubFiltersAsPassThrough() throws Exception {
        doAnswer(this::invokeChain).when(jwtAuthenticationFilter)
                .doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
        doAnswer(this::invokeChain).when(internalApiKeyFilter)
                .doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
    }

    private Object invokeChain(InvocationOnMock invocation) throws Exception {
        ServletRequest req = invocation.getArgument(0);
        ServletResponse res = invocation.getArgument(1);
        FilterChain chain = invocation.getArgument(2);
        chain.doFilter(req, res);
        return null;
    }

    @Test
    @DisplayName("/api/v1/admin/ping — 비인증은 401")
    void ping_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/api/v1/admin/ping — ROLE_USER는 403")
    void ping_user_403() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        mockMvc.perform(get("/api/v1/admin/ping").with(authentication(auth)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/api/v1/admin/ping — ROLE_ADMIN은 200")
    void ping_admin_200() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mockMvc.perform(get("/api/v1/admin/ping").with(authentication(auth)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RoleHierarchy 빈 — ROLE_ADMIN은 ROLE_USER 권한을 포함한다")
    void roleHierarchy_adminInheritsUser() {
        var adminAuthority = new SimpleGrantedAuthority("ROLE_ADMIN");

        var reachable = roleHierarchy.getReachableGrantedAuthorities(List.of(adminAuthority));

        assertThat(reachable).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN", "ROLE_USER");
    }
}
