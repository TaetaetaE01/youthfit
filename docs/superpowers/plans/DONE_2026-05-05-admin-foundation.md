# 어드민 페이지 — Spec 1: 공통 기반 (Foundation) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** YouthFit에 ROLE_ADMIN 사용자만 진입 가능한 `/admin` 영역을 신설한다 (인증·라우팅·레이아웃 + ping 엔드포인트). 후속 spec(이메일/Q&A 캐시/LLM 비용/Ingestion) 4개가 합류할 토대.

**Architecture:** 기존 카카오 로그인 + JWT(role 포함) 인프라를 그대로 재사용한다. `SecurityConfig`에 `/api/v1/admin/**` → `hasRole("ADMIN")` 규칙과 `RoleHierarchy` 빈을 추가하고, 새 `admin` 도메인 모듈을 만들어 ping 엔드포인트만 둔다. 프론트는 기존 React SPA에 `/admin/*` 라우트와 `RequireAdmin` 가드를 추가한다.

**Tech Stack:** Java 21, Spring Boot 4.0.5, Spring Security, JUnit 5, MockMvc / React 19, TypeScript 5, React Router v7, Zustand v5, TanStack Query v5, Vitest, Testing Library

**Spec:** `docs/superpowers/specs/2026-05-05-admin-foundation-design.md`

---

## Task 0: 사전 확인 — DB `users.role` 컬럼

**Goal:** 첫 spec 구현 전 실제 DB에 `users.role` 컬럼이 존재하고 디폴트가 `'USER'`인지 확인.

- [ ] **Step 1: DB 스키마 확인**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
docker compose exec postgres psql -U youthfit -d youthfit -c "\d users"
```

Expected: `role` 컬럼이 `character varying(10)`로 존재하고 NOT NULL.

- [ ] **Step 2: 컬럼 누락 시 Flyway 마이그레이션 추가 (조건부)**

컬럼이 이미 존재하면 이 step은 스킵. 누락 시 다음 두 명령으로 다음 마이그레이션 번호 확인 후 파일 생성:

```bash
ls backend/src/main/resources/db/migration/ | sort | tail -3
```

가장 마지막 번호 + 1을 사용하여 (예: `V42__...`가 마지막이면 `V43__add_users_role.sql`) 신설:

```sql
ALTER TABLE users
    ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'USER';
```

- [ ] **Step 3: (조건부) 마이그레이션 적용 확인**

```bash
cd backend && ./gradlew flywayMigrate
```

Expected: SUCCESS, 다시 `\d users`로 컬럼 확인.

- [ ] **Step 4: 커밋 (조건부)**

마이그레이션을 추가했을 때만:

```bash
git add backend/src/main/resources/db/migration/V*__add_users_role.sql
git commit -m "chore(db): users.role 컬럼 추가 마이그레이션"
```

---

## Task 1: 백엔드 — `UserProfileResult`에 role 매핑

**Files:**
- Modify: `backend/src/main/java/com/youthfit/user/application/dto/result/UserProfileResult.java`
- Test: `backend/src/test/java/com/youthfit/user/application/dto/result/UserProfileResultTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`backend/src/test/java/com/youthfit/user/application/dto/result/UserProfileResultTest.java`:

```java
package com.youthfit.user.application.dto.result;

import com.youthfit.user.domain.model.AuthProvider;
import com.youthfit.user.domain.model.Role;
import com.youthfit.user.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserProfileResult")
class UserProfileResultTest {

    @Test
    @DisplayName("from(User)는 User의 role을 그대로 매핑한다")
    void from_mapsRole() {
        User user = User.builder()
                .email("a@x.com")
                .nickname("nick")
                .authProvider(AuthProvider.KAKAO)
                .providerId("p1")
                .build();

        UserProfileResult result = UserProfileResult.from(user);

        assertThat(result.role()).isEqualTo(Role.USER);
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.user.application.dto.result.UserProfileResultTest"
```

Expected: COMPILATION FAILURE — `UserProfileResult.role()` 메서드 없음.

- [ ] **Step 3: `UserProfileResult`에 role 필드 추가**

`backend/src/main/java/com/youthfit/user/application/dto/result/UserProfileResult.java`:

```java
package com.youthfit.user.application.dto.result;

import com.youthfit.user.domain.model.Role;
import com.youthfit.user.domain.model.User;

import java.time.LocalDateTime;

public record UserProfileResult(
        Long id,
        String email,
        String nickname,
        String profileImageUrl,
        Role role,
        LocalDateTime createdAt
) {
    public static UserProfileResult from(User user) {
        return new UserProfileResult(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
```

- [ ] **Step 4: 테스트 실행 — 성공 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.user.application.dto.result.UserProfileResultTest"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 컴파일 영향 받는 호출자 빠르게 확인**

```bash
cd backend && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL. 만약 다른 곳에서 `new UserProfileResult(...)`로 직접 생성하는 코드가 있다면 컴파일 에러 발생 — Step 3의 record 필드 순서대로 인자 추가.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/user/application/dto/result/UserProfileResult.java \
        backend/src/test/java/com/youthfit/user/application/dto/result/UserProfileResultTest.java
git commit -m "feat(user): UserProfileResult에 role 매핑 추가"
```

---

## Task 2: 백엔드 — `UserProfileResponse`에 role 노출

**Files:**
- Modify: `backend/src/main/java/com/youthfit/user/presentation/dto/response/UserProfileResponse.java`
- Test: `backend/src/test/java/com/youthfit/user/presentation/dto/response/UserProfileResponseTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`backend/src/test/java/com/youthfit/user/presentation/dto/response/UserProfileResponseTest.java`:

```java
package com.youthfit.user.presentation.dto.response;

import com.youthfit.user.application.dto.result.UserProfileResult;
import com.youthfit.user.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserProfileResponse")
class UserProfileResponseTest {

    @Test
    @DisplayName("from(Result)는 Role enum을 문자열로 노출한다")
    void from_exposesRoleAsString() {
        UserProfileResult result = new UserProfileResult(
                1L, "a@x.com", "nick", null,
                Role.ADMIN,
                LocalDateTime.of(2026, 5, 5, 10, 0)
        );

        UserProfileResponse response = UserProfileResponse.from(result);

        assertThat(response.role()).isEqualTo("ADMIN");
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.user.presentation.dto.response.UserProfileResponseTest"
```

Expected: COMPILATION FAILURE — `UserProfileResponse.role()` 없음.

- [ ] **Step 3: `UserProfileResponse`에 role 필드 추가**

`backend/src/main/java/com/youthfit/user/presentation/dto/response/UserProfileResponse.java`:

```java
package com.youthfit.user.presentation.dto.response;

import com.youthfit.user.application.dto.result.UserProfileResult;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        String email,
        String nickname,
        String profileImageUrl,
        String role,
        LocalDateTime createdAt
) {
    public static UserProfileResponse from(UserProfileResult result) {
        return new UserProfileResponse(
                result.id(),
                result.email(),
                result.nickname(),
                result.profileImageUrl(),
                result.role().name(),
                result.createdAt()
        );
    }
}
```

- [ ] **Step 4: 테스트 실행 — 성공 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.user.presentation.dto.response.UserProfileResponseTest"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/youthfit/user/presentation/dto/response/UserProfileResponse.java \
        backend/src/test/java/com/youthfit/user/presentation/dto/response/UserProfileResponseTest.java
git commit -m "feat(user): UserProfileResponse에 role 필드 노출"
```

---

## Task 3: 백엔드 — `admin` 모듈 신설 (AdminPingResponse / Api / Controller)

**Files:**
- Create: `backend/src/main/java/com/youthfit/admin/presentation/dto/response/AdminPingResponse.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/controller/AdminPingApi.java`
- Create: `backend/src/main/java/com/youthfit/admin/presentation/controller/AdminPingController.java`
- Test: `backend/src/test/java/com/youthfit/admin/presentation/controller/AdminPingControllerTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`backend/src/test/java/com/youthfit/admin/presentation/controller/AdminPingControllerTest.java`:

```java
package com.youthfit.admin.presentation.controller;

import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminPingController")
@WebMvcTest(controllers = AdminPingController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class}))
class AdminPingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/admin/ping - 인증된 호출은 pong을 반환한다")
    void ping_returnsPong() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mockMvc.perform(get("/api/v1/admin/ping").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("pong"))
                .andExpect(jsonPath("$.data.serverTime").exists());
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.admin.presentation.controller.AdminPingControllerTest"
```

Expected: COMPILATION FAILURE — admin 패키지 없음.

- [ ] **Step 3: 응답 DTO 생성**

`backend/src/main/java/com/youthfit/admin/presentation/dto/response/AdminPingResponse.java`:

```java
package com.youthfit.admin.presentation.dto.response;

import java.time.LocalDateTime;

public record AdminPingResponse(
        String message,
        LocalDateTime serverTime
) {
    public static AdminPingResponse pong() {
        return new AdminPingResponse("pong", LocalDateTime.now());
    }
}
```

- [ ] **Step 4: API 인터페이스 생성**

`backend/src/main/java/com/youthfit/admin/presentation/controller/AdminPingApi.java`:

```java
package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.presentation.dto.response.AdminPingResponse;
import com.youthfit.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin", description = "어드민 전용 API (ROLE_ADMIN 필수)")
public interface AdminPingApi {

    @Operation(summary = "어드민 ping",
            description = "ROLE_ADMIN 권한 확인용 스모크 엔드포인트")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "pong"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족")
    })
    ResponseEntity<ApiResponse<AdminPingResponse>> ping();
}
```

- [ ] **Step 5: Controller 생성**

`backend/src/main/java/com/youthfit/admin/presentation/controller/AdminPingController.java`:

```java
package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.presentation.dto.response.AdminPingResponse;
import com.youthfit.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminPingController implements AdminPingApi {

    @GetMapping("/ping")
    @Override
    public ResponseEntity<ApiResponse<AdminPingResponse>> ping() {
        return ResponseEntity.ok(ApiResponse.ok(AdminPingResponse.pong()));
    }
}
```

- [ ] **Step 6: 테스트 실행 — 성공 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.admin.presentation.controller.AdminPingControllerTest"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 커밋**

```bash
git add backend/src/main/java/com/youthfit/admin/ \
        backend/src/test/java/com/youthfit/admin/
git commit -m "feat(admin): admin 모듈 신설 + GET /api/v1/admin/ping 엔드포인트"
```

---

## Task 4: 백엔드 — `SecurityConfig` 어드민 보호 + RoleHierarchy

**Files:**
- Modify: `backend/src/main/java/com/youthfit/common/config/SecurityConfig.java`
- Test: `backend/src/test/java/com/youthfit/common/config/AdminSecurityIntegrationTest.java`

- [ ] **Step 1: 실패 테스트 작성 (실 SecurityConfig 적용 통합 테스트)**

`backend/src/test/java/com/youthfit/common/config/AdminSecurityIntegrationTest.java`:

```java
package com.youthfit.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("어드민 보안 통합")
class AdminSecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("/api/v1/admin/ping — 비인증은 401")
    void ping_unauthenticated_401() throws Exception {
        mockMvc().perform(get("/api/v1/admin/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/api/v1/admin/ping — ROLE_USER는 403")
    void ping_user_403() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        mockMvc().perform(get("/api/v1/admin/ping").with(authentication(auth)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/api/v1/admin/ping — ROLE_ADMIN은 200")
    void ping_admin_200() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mockMvc().perform(get("/api/v1/admin/ping").with(authentication(auth)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RoleHierarchy — ROLE_ADMIN은 ROLE_USER 전용 엔드포인트(/api/v1/users/me)도 통과")
    void admin_canAccessUserEndpoint() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        // 인증 통과만 검증 — 200/404 등 컨트롤러 결과는 무관
        mockMvc().perform(get("/api/v1/users/me").with(authentication(auth)))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));
    }
}
```

> 참고: `/api/v1/users/me` 호출 시 사용자 데이터 없음으로 다른 에러가 날 수 있으나 본 테스트는 **403 아님**만 검증한다.

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.common.config.AdminSecurityIntegrationTest"
```

Expected: 일부 케이스 실패. 특히 `ping_user_403`는 현재 SecurityConfig가 단순 `authenticated()`만 요구해서 200 반환 → 실패.

- [ ] **Step 3: `SecurityConfig`에 admin 보호 + RoleHierarchy 추가**

`backend/src/main/java/com/youthfit/common/config/SecurityConfig.java` 전문 (변경 부분 강조):

```java
package com.youthfit.common.config;

import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationEntryPoint;
import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final InternalApiKeyFilter internalApiKeyFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/policies/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/policies/attachments/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/guides/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/regions").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/internal/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")  // ← 추가
                        .anyRequest().authenticated()
                )
                .addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_USER");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

> 변경 라인:
> - import 두 개 추가 (`RoleHierarchy`, `RoleHierarchyImpl`)
> - `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")` 한 줄 (anyRequest 위)
> - `roleHierarchy()` 빈 메서드 추가

- [ ] **Step 4: 테스트 실행 — 성공 확인**

```bash
cd backend && ./gradlew test --tests "com.youthfit.common.config.AdminSecurityIntegrationTest"
```

Expected: BUILD SUCCESSFUL (4 tests pass).

- [ ] **Step 5: 전체 테스트 회귀 검증**

```bash
cd backend && ./gradlew test
```

Expected: 기존 테스트 모두 그린.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/youthfit/common/config/SecurityConfig.java \
        backend/src/test/java/com/youthfit/common/config/AdminSecurityIntegrationTest.java
git commit -m "feat(security): /api/v1/admin/** ROLE_ADMIN 보호 + RoleHierarchy"
```

---

## Task 5: 프론트 — `UserProfile` 타입에 role 추가

**Files:**
- Modify: `frontend/src/types/policy.ts`(또는 UserProfile 정의 파일)
- Test: 본 task는 타입 변경만이라 별도 단위 테스트 없음. 컴파일러가 검증.

- [ ] **Step 1: UserProfile 타입 정의 위치 찾기**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/frontend
grep -rn "interface UserProfile\|type UserProfile" src/types/
```

Expected: `src/types/policy.ts` 또는 user 관련 파일에서 발견.

- [ ] **Step 2: `UserProfile`에 `role` 필드 추가**

해당 파일 (예: `frontend/src/types/policy.ts`)의 `UserProfile`에 다음 필드 추가:

```ts
export interface UserProfile {
  id: number;
  email: string;
  nickname: string;
  profileImageUrl?: string | null;
  role: 'USER' | 'ADMIN';   // ← 추가
  createdAt: string;
}
```

> 기존 필드 정확한 형태는 grep 결과를 따라간다. `role` 한 필드만 추가.

- [ ] **Step 3: 타입 체크**

```bash
cd frontend && npm run build
```

Expected: BUILD SUCCESSFUL. 만약 `UserProfile`을 사용하는 컴포넌트가 `role` 누락으로 에러 나면, 그곳도 수정해야 하지만 목 데이터 외에는 거의 없을 것 — me() 응답으로 채워지는 곳이라 자동 적용됨.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/types/
git commit -m "feat(types): UserProfile에 role 필드 추가"
```

---

## Task 6: 프론트 — `authStore`에 role 보관 + me() 호출 흐름

**Files:**
- Modify: `frontend/src/stores/authStore.ts`
- Modify: `frontend/src/pages/KakaoCallbackPage.tsx`
- Test: `frontend/src/stores/__tests__/authStore.test.ts`

- [ ] **Step 1: authStore 실패 테스트 작성**

`frontend/src/stores/__tests__/authStore.test.ts`:

```ts
import { describe, it, expect, beforeEach } from 'vitest';
import { useAuthStore } from '@/stores/authStore';

describe('authStore', () => {
  beforeEach(() => {
    localStorage.clear();
    useAuthStore.setState({ accessToken: null, isAuthenticated: false, role: null });
  });

  it('login(token, role) 호출 시 accessToken과 role을 저장한다', () => {
    useAuthStore.getState().login('tok-123', 'ADMIN');

    const s = useAuthStore.getState();
    expect(s.accessToken).toBe('tok-123');
    expect(s.isAuthenticated).toBe(true);
    expect(s.role).toBe('ADMIN');
  });

  it('logout 시 role도 초기화된다', () => {
    useAuthStore.getState().login('tok-123', 'ADMIN');
    useAuthStore.getState().logout();

    const s = useAuthStore.getState();
    expect(s.accessToken).toBeNull();
    expect(s.isAuthenticated).toBe(false);
    expect(s.role).toBeNull();
  });
});
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd frontend && npm run test -- --run src/stores/__tests__/authStore.test.ts
```

Expected: FAIL — `login`이 두 번째 인자를 받지 않음, `role` 필드 없음.

- [ ] **Step 3: authStore에 role 추가**

`frontend/src/stores/authStore.ts`:

```ts
import { create } from 'zustand';

type Role = 'USER' | 'ADMIN';

interface AuthState {
  accessToken: string | null;
  isAuthenticated: boolean;
  role: Role | null;
  login: (token: string, role?: Role | null) => void;
  setRole: (role: Role | null) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: localStorage.getItem('accessToken'),
  isAuthenticated: !!localStorage.getItem('accessToken'),
  role: (localStorage.getItem('role') as Role | null) ?? null,
  login: (token, role = null) => {
    localStorage.setItem('accessToken', token);
    if (role) localStorage.setItem('role', role);
    else localStorage.removeItem('role');
    set({ accessToken: token, isAuthenticated: true, role });
  },
  setRole: (role) => {
    if (role) localStorage.setItem('role', role);
    else localStorage.removeItem('role');
    set({ role });
  },
  logout: () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('role');
    set({ accessToken: null, isAuthenticated: false, role: null });
  },
}));
```

- [ ] **Step 4: authStore 테스트 통과 확인**

```bash
cd frontend && npm run test -- --run src/stores/__tests__/authStore.test.ts
```

Expected: PASS.

- [ ] **Step 5: KakaoCallbackPage에서 me() 호출 후 role 저장**

`frontend/src/pages/KakaoCallbackPage.tsx`:

```tsx
import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { loginWithKakao } from '@/apis/auth.api';
import { fetchProfile } from '@/apis/user.api';
import { useAuthStore } from '@/stores/authStore';

export default function KakaoCallbackPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const login = useAuthStore((s) => s.login);
  const setRole = useAuthStore((s) => s.setRole);
  const [error, setError] = useState(false);

  useEffect(() => {
    const code = searchParams.get('code');
    const redirect = searchParams.get('state') || '/policies';

    if (!code) {
      setError(true);
      return;
    }

    loginWithKakao(code)
      .then(async (tokens) => {
        login(tokens.accessToken);
        try {
          const me = await fetchProfile();
          setRole(me.role ?? null);
        } catch {
          setRole(null);
        }
        navigate(redirect, { replace: true });
      })
      .catch(() => {
        setError(true);
        setTimeout(() => navigate('/login', { replace: true }), 2000);
      });
  }, []);

  if (error) {
    return (
      <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3">
        <p className="text-sm text-error-500">로그인에 실패했어요. 다시 시도해주세요.</p>
        <p className="text-xs text-gray-400">잠시 후 로그인 페이지로 이동합니다...</p>
      </div>
    );
  }

  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4">
      <Loader2 className="h-8 w-8 animate-spin text-brand-800" />
      <p className="text-sm font-medium text-gray-600">로그인 중이에요...</p>
    </div>
  );
}
```

- [ ] **Step 6: 빌드 확인**

```bash
cd frontend && npm run build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 커밋**

```bash
git add frontend/src/stores/authStore.ts \
        frontend/src/stores/__tests__/authStore.test.ts \
        frontend/src/pages/KakaoCallbackPage.tsx
git commit -m "feat(auth): authStore에 role 보관 + 콜백에서 me 호출하여 role 저장"
```

---

## Task 7: 프론트 — admin API 함수 + useAdminPing 훅

**Files:**
- Create: `frontend/src/apis/admin.api.ts`
- Create: `frontend/src/hooks/queries/useAdminPing.ts`

- [ ] **Step 1: admin.api.ts 생성**

`frontend/src/apis/admin.api.ts`:

```ts
import api from './client';

export interface AdminPingResponse {
  message: string;
  serverTime: string;
}

interface ApiEnvelope<T> { data: T }

export async function pingAdmin(): Promise<AdminPingResponse> {
  const res = await api.get('v1/admin/ping').json<ApiEnvelope<AdminPingResponse>>();
  return res.data;
}
```

- [ ] **Step 2: useAdminPing 훅 생성**

`frontend/src/hooks/queries/useAdminPing.ts`:

```ts
import { useQuery } from '@tanstack/react-query';
import { pingAdmin } from '@/apis/admin.api';

export function useAdminPing() {
  return useQuery({
    queryKey: ['admin', 'ping'],
    queryFn: pingAdmin,
    staleTime: 0,
    retry: 0,
  });
}
```

- [ ] **Step 3: 빌드 확인**

```bash
cd frontend && npm run build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/apis/admin.api.ts frontend/src/hooks/queries/useAdminPing.ts
git commit -m "feat(admin): admin API 함수 + useAdminPing 훅 추가"
```

---

## Task 8: 프론트 — `RequireAdmin` 가드 컴포넌트

**Files:**
- Create: `frontend/src/components/auth/RequireAdmin.tsx`
- Test: `frontend/src/components/auth/__tests__/RequireAdmin.test.tsx`

- [ ] **Step 1: 실패 테스트 작성**

`frontend/src/components/auth/__tests__/RequireAdmin.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { RequireAdmin } from '../RequireAdmin';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<div>LOGIN_PAGE</div>} />
        <Route path="/" element={<div>HOME_PAGE</div>} />
        <Route element={<RequireAdmin />}>
          <Route path="/admin" element={<div>ADMIN_AREA</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('RequireAdmin', () => {
  beforeEach(() => {
    localStorage.clear();
    useAuthStore.setState({ accessToken: null, isAuthenticated: false, role: null });
  });

  it('비인증 → 로그인 페이지로 리다이렉트', () => {
    renderAt('/admin');
    expect(screen.getByText('LOGIN_PAGE')).toBeInTheDocument();
  });

  it('role=USER → 홈으로 리다이렉트', () => {
    useAuthStore.setState({ accessToken: 't', isAuthenticated: true, role: 'USER' });
    renderAt('/admin');
    expect(screen.getByText('HOME_PAGE')).toBeInTheDocument();
  });

  it('role=ADMIN → 어드민 영역 렌더', () => {
    useAuthStore.setState({ accessToken: 't', isAuthenticated: true, role: 'ADMIN' });
    renderAt('/admin');
    expect(screen.getByText('ADMIN_AREA')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd frontend && npm run test -- --run src/components/auth/__tests__/RequireAdmin.test.tsx
```

Expected: FAIL — RequireAdmin 미존재.

- [ ] **Step 3: RequireAdmin 컴포넌트 작성**

`frontend/src/components/auth/RequireAdmin.tsx`:

```tsx
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';

export function RequireAdmin() {
  const location = useLocation();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const role = useAuthStore((s) => s.role);

  if (!isAuthenticated) {
    const target = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect_to=${target}`} replace />;
  }
  if (role !== 'ADMIN') {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
```

- [ ] **Step 4: 테스트 실행 — 성공 확인**

```bash
cd frontend && npm run test -- --run src/components/auth/__tests__/RequireAdmin.test.tsx
```

Expected: PASS (3 tests).

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/auth/RequireAdmin.tsx \
        frontend/src/components/auth/__tests__/RequireAdmin.test.tsx
git commit -m "feat(admin): RequireAdmin 라우트 가드 컴포넌트"
```

---

## Task 9: 프론트 — `AdminLayout` (사이드바 + 헤더)

**Files:**
- Create: `frontend/src/components/layout/AdminLayout.tsx`
- Test: `frontend/src/components/layout/__tests__/AdminLayout.test.tsx`

- [ ] **Step 1: 실패 테스트 작성**

`frontend/src/components/layout/__tests__/AdminLayout.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import AdminLayout from '../AdminLayout';

function renderLayout() {
  return render(
    <MemoryRouter initialEntries={['/admin']}>
      <Routes>
        <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<div>DASHBOARD</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('AdminLayout', () => {
  it('사이드바에 4개 메뉴를 노출한다', () => {
    renderLayout();
    expect(screen.getByText('이메일 발송')).toBeInTheDocument();
    expect(screen.getByText('Q&A 캐시 로그')).toBeInTheDocument();
    expect(screen.getByText('LLM 비용')).toBeInTheDocument();
    expect(screen.getByText('Ingestion 헬스')).toBeInTheDocument();
  });

  it('자식 라우트(Outlet) 컨텐츠를 렌더한다', () => {
    renderLayout();
    expect(screen.getByText('DASHBOARD')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd frontend && npm run test -- --run src/components/layout/__tests__/AdminLayout.test.tsx
```

Expected: FAIL — AdminLayout 미존재.

- [ ] **Step 3: AdminLayout 작성**

`frontend/src/components/layout/AdminLayout.tsx`:

```tsx
import { Link, NavLink, Outlet } from 'react-router-dom';

const MENU = [
  { to: '/admin', label: '대시보드', end: true },
  { to: '/admin/email', label: '이메일 발송', soon: true },
  { to: '/admin/qna-cache', label: 'Q&A 캐시 로그', soon: true },
  { to: '/admin/llm-cost', label: 'LLM 비용', soon: true },
  { to: '/admin/ingestion', label: 'Ingestion 헬스', soon: true },
];

export default function AdminLayout() {
  return (
    <div className="flex min-h-screen bg-gray-50">
      <aside className="w-60 shrink-0 border-r bg-white p-4">
        <Link to="/admin" className="mb-6 block text-lg font-bold">
          YouthFit Admin
        </Link>
        <nav className="flex flex-col gap-1">
          {MENU.map((m) => (
            <NavLink
              key={m.to}
              to={m.to}
              end={m.end}
              className={({ isActive }) =>
                `rounded px-3 py-2 text-sm ${isActive ? 'bg-brand-50 font-semibold text-brand-700' : 'text-gray-700 hover:bg-gray-100'}`
              }
              onClick={(e) => {
                if (m.soon) {
                  e.preventDefault();
                  alert(`${m.label} — 준비 중입니다`);
                }
              }}
            >
              {m.label}
              {m.soon && <span className="ml-2 text-xs text-gray-400">(준비 중)</span>}
            </NavLink>
          ))}
        </nav>
      </aside>
      <main className="flex-1 p-6">
        <Outlet />
      </main>
    </div>
  );
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd frontend && npm run test -- --run src/components/layout/__tests__/AdminLayout.test.tsx
```

Expected: PASS (2 tests).

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/layout/AdminLayout.tsx \
        frontend/src/components/layout/__tests__/AdminLayout.test.tsx
git commit -m "feat(admin): AdminLayout 사이드바 + Outlet 레이아웃"
```

---

## Task 10: 프론트 — `AdminDashboardPage`

**Files:**
- Create: `frontend/src/pages/admin/AdminDashboardPage.tsx`
- Test: `frontend/src/pages/admin/__tests__/AdminDashboardPage.test.tsx`

- [ ] **Step 1: 실패 테스트 작성**

`frontend/src/pages/admin/__tests__/AdminDashboardPage.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import AdminDashboardPage from '../AdminDashboardPage';

vi.mock('@/apis/admin.api', () => ({
  pingAdmin: vi.fn(),
}));

import { pingAdmin } from '@/apis/admin.api';

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AdminDashboardPage />
    </QueryClientProvider>,
  );
}

describe('AdminDashboardPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('카드 4개를 노출한다', () => {
    (pingAdmin as ReturnType<typeof vi.fn>).mockResolvedValue({ message: 'pong', serverTime: '2026-05-05T10:00:00' });
    renderPage();
    expect(screen.getByText('이메일 발송')).toBeInTheDocument();
    expect(screen.getByText('Q&A 캐시 로그')).toBeInTheDocument();
    expect(screen.getByText('LLM 비용')).toBeInTheDocument();
    expect(screen.getByText('Ingestion 헬스')).toBeInTheDocument();
  });

  it('ping 성공 시 pong을 표시한다', async () => {
    (pingAdmin as ReturnType<typeof vi.fn>).mockResolvedValue({ message: 'pong', serverTime: '2026-05-05T10:00:00' });
    renderPage();
    await waitFor(() => expect(screen.getByText(/pong/i)).toBeInTheDocument());
  });

  it('ping 실패 시 에러 메시지 표시', async () => {
    (pingAdmin as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('boom'));
    renderPage();
    await waitFor(() => expect(screen.getByText(/연결 실패|에러|실패/)).toBeInTheDocument());
  });
});
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
cd frontend && npm run test -- --run src/pages/admin/__tests__/AdminDashboardPage.test.tsx
```

Expected: FAIL — AdminDashboardPage 미존재.

- [ ] **Step 3: AdminDashboardPage 작성**

`frontend/src/pages/admin/AdminDashboardPage.tsx`:

```tsx
import { useAdminPing } from '@/hooks/queries/useAdminPing';

const CARDS = [
  { title: '이메일 발송', desc: '성공/실패/바운스 추적' },
  { title: 'Q&A 캐시 로그', desc: 'semantic-cache hit/miss' },
  { title: 'LLM 비용', desc: '토큰/비용 집계' },
  { title: 'Ingestion 헬스', desc: '수신/정규화 통계' },
];

export default function AdminDashboardPage() {
  const ping = useAdminPing();

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-bold">관리자 대시보드</h1>
        <p className="mt-1 text-sm text-gray-500">운영 추적 영역. 항목별 상세는 후속 출시 예정.</p>
      </header>

      <section className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {CARDS.map((c) => (
          <div key={c.title} className="rounded border bg-white p-4">
            <h2 className="text-base font-semibold">{c.title}</h2>
            <p className="mt-1 text-sm text-gray-500">{c.desc}</p>
            <p className="mt-3 text-xs text-gray-400">준비 중</p>
          </div>
        ))}
      </section>

      <footer className="rounded border bg-white p-3 text-xs text-gray-500">
        {ping.isLoading && '어드민 API 확인 중…'}
        {ping.isError && (
          <span className="text-error-600">어드민 API 연결 실패 — 권한 또는 서버 상태를 확인하세요.</span>
        )}
        {ping.data && <span>어드민 API: <strong>{ping.data.message}</strong> · {ping.data.serverTime}</span>}
      </footer>
    </div>
  );
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd frontend && npm run test -- --run src/pages/admin/__tests__/AdminDashboardPage.test.tsx
```

Expected: PASS (3 tests).

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/pages/admin/AdminDashboardPage.tsx \
        frontend/src/pages/admin/__tests__/AdminDashboardPage.test.tsx
git commit -m "feat(admin): AdminDashboardPage 카드 4개 + ping 스모크"
```

---

## Task 11: 프론트 — `App.tsx` 라우트 통합

**Files:**
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: App.tsx에 어드민 라우트 추가**

`frontend/src/App.tsx`:

```tsx
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import AppLayout from '@/components/layout/AppLayout';
import AdminLayout from '@/components/layout/AdminLayout';
import { RequireAdmin } from '@/components/auth/RequireAdmin';
import LandingPage from '@/pages/LandingPage';
import PolicyListPage from '@/pages/PolicyListPage';
import PolicyDetailPage from '@/pages/PolicyDetailPage';
import LoginPage from '@/pages/LoginPage';
import KakaoCallbackPage from '@/pages/KakaoCallbackPage';
import MyPage from '@/pages/MyPage';
import AdminDashboardPage from '@/pages/admin/AdminDashboardPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5,
      retry: 1,
    },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<LandingPage />} />

          <Route element={<AppLayout />}>
            <Route path="/policies" element={<PolicyListPage />} />
            <Route path="/policies/:policyId" element={<PolicyDetailPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/auth/kakao/callback" element={<KakaoCallbackPage />} />
            <Route path="/mypage" element={<MyPage />} />
          </Route>

          {/* 어드민 — RequireAdmin → AdminLayout → 자식 페이지 */}
          <Route element={<RequireAdmin />}>
            <Route path="/admin" element={<AdminLayout />}>
              <Route index element={<AdminDashboardPage />} />
              {/* 후속 spec에서 자식 라우트 추가 */}
            </Route>
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
```

- [ ] **Step 2: 빌드 + 전체 프론트 테스트 회귀**

```bash
cd frontend && npm run build && npm run test -- --run
```

Expected: BUILD SUCCESSFUL + 전체 테스트 그린.

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/App.tsx
git commit -m "feat(admin): App.tsx에 /admin 라우트 + RequireAdmin 가드 통합"
```

---

## Task 12: 통합 검증 (수동) + ADMIN 임명

**Files:** 없음 (운영 검증).

- [ ] **Step 1: 백엔드/프론트 동시 기동**

```bash
# 터미널 1
cd backend && ./gradlew bootRun

# 터미널 2
cd frontend && npm run dev
```

Expected: 백엔드 :8080, 프론트 :5173 정상 기동.

- [ ] **Step 2: 본인 카카오로 1회 로그인**

브라우저에서 http://localhost:5173/login → 카카오 로그인 → `/policies`로 redirect 되어야 함.

- [ ] **Step 3: DB에서 본인 user를 ADMIN으로 변경**

```bash
docker compose exec postgres psql -U youthfit -d youthfit \
  -c "UPDATE users SET role = 'ADMIN' WHERE email = '본인 카카오 이메일';"
```

Expected: `UPDATE 1`.

- [ ] **Step 4: 재로그인 (JWT에 role이 박혀 있으므로 필수)**

브라우저에서 로그아웃 → 다시 카카오 로그인.

- [ ] **Step 5: `/admin` 진입 확인**

브라우저에서 http://localhost:5173/admin 접속:
- 사이드바 4개 메뉴 노출
- 카드 4개 placeholder
- 하단에 `어드민 API: pong · <서버 시각>` 표시 확인

- [ ] **Step 6: 비-어드민 계정 검증**

다른 카카오 계정(role=USER) 또는 DB에서 일시적으로 role을 USER로 되돌려 재로그인 → `/admin` 접속 시 `/`로 redirect되는지 확인.

- [ ] **Step 7: ROLE_ADMIN으로 일반 페이지 동작 확인**

ADMIN 계정으로 `/policies`, `/mypage` 등 정상 동작 확인 (RoleHierarchy 검증).

- [ ] **Step 8: PR 생성**

```bash
git push -u origin <branch>
gh pr create --title "feat(admin): 어드민 페이지 공통 기반(Spec 1)" --body "$(cat <<'EOF'
## Summary
- ROLE_ADMIN만 접근 가능한 /admin 영역 토대 구축
- /api/v1/admin/** 보호 + RoleHierarchy 빈 추가
- 카카오 로그인 → me() 호출하여 role을 authStore에 저장
- AdminLayout 사이드바 4개 메뉴 (후속 spec에서 채움)
- AdminDashboardPage 카드 4개 placeholder + ping 스모크

## Test plan
- [ ] 백엔드 SecurityConfig 통합 테스트 4개 그린
- [ ] AdminPingControllerTest 그린
- [ ] UserProfileResult/Response 단위 테스트 그린
- [ ] RequireAdmin / AdminLayout / AdminDashboardPage / authStore 프론트 테스트 그린
- [ ] 수동: ADMIN 계정 /admin 접근 가능
- [ ] 수동: USER 계정 /admin 접근 시 redirect
- [ ] 수동: ADMIN 계정으로 일반 페이지 정상 동작 (RoleHierarchy)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## 부록: 변경 파일 요약

**백엔드 신규**
- `admin/presentation/controller/AdminPingApi.java`
- `admin/presentation/controller/AdminPingController.java`
- `admin/presentation/dto/response/AdminPingResponse.java`
- `test/.../admin/presentation/controller/AdminPingControllerTest.java`
- `test/.../common/config/AdminSecurityIntegrationTest.java`
- `test/.../user/application/dto/result/UserProfileResultTest.java`
- `test/.../user/presentation/dto/response/UserProfileResponseTest.java`

**백엔드 수정**
- `common/config/SecurityConfig.java`
- `user/application/dto/result/UserProfileResult.java`
- `user/presentation/dto/response/UserProfileResponse.java`

**프론트 신규**
- `components/auth/RequireAdmin.tsx` + `__tests__/RequireAdmin.test.tsx`
- `components/layout/AdminLayout.tsx` + `__tests__/AdminLayout.test.tsx`
- `pages/admin/AdminDashboardPage.tsx` + `__tests__/AdminDashboardPage.test.tsx`
- `apis/admin.api.ts`
- `hooks/queries/useAdminPing.ts`
- `stores/__tests__/authStore.test.ts`

**프론트 수정**
- `App.tsx` (어드민 라우트)
- `stores/authStore.ts` (role 추가)
- `pages/KakaoCallbackPage.tsx` (me() 호출)
- `types/policy.ts`(또는 user 타입 파일) — `UserProfile.role`
