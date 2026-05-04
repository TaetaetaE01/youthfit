package com.youthfit.user.application.service;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.application.dto.result.EmailContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationEmailRenderer")
class NotificationEmailRendererTest {

    private NotificationEmailRenderer renderer;

    @BeforeEach
    void setUp() {
        TemplateEngine htmlEngine = newEngine("HTML", ".html");
        TemplateEngine textEngine = newEngine("TEXT", ".txt");
        renderer = new NotificationEmailRenderer(htmlEngine, textEngine, "https://youthfit.test");
    }

    @Test
    @DisplayName("renderDeadline: subject 에 정책명이 포함된다")
    void renderDeadline_subjectIncludesTitle() {
        // given
        Policy policy = createPolicy(10L, "청년 취업 지원", LocalDate.of(2026, 6, 30));

        // when
        EmailContent content = renderer.renderDeadline(policy);

        // then
        assertThat(content.subject()).contains("청년 취업 지원");
        assertThat(content.subject()).contains("마감");
    }

    @Test
    @DisplayName("renderDeadline: htmlBody 에 정책명·마감일·상세 링크가 포함된다")
    void renderDeadline_htmlBodyIncludesAll() {
        // given
        Policy policy = createPolicy(10L, "청년 취업 지원", LocalDate.of(2026, 6, 30));

        // when
        EmailContent content = renderer.renderDeadline(policy);

        // then
        assertThat(content.htmlBody()).contains("청년 취업 지원");
        assertThat(content.htmlBody()).contains("2026-06-30");
        assertThat(content.htmlBody()).contains("https://youthfit.test/policies/10");
    }

    @Test
    @DisplayName("renderDeadline: textBody 에도 같은 정보가 포함된다")
    void renderDeadline_textBodyIncludesAll() {
        // given
        Policy policy = createPolicy(10L, "청년 취업 지원", LocalDate.of(2026, 6, 30));

        // when
        EmailContent content = renderer.renderDeadline(policy);

        // then
        assertThat(content.textBody()).contains("청년 취업 지원");
        assertThat(content.textBody()).contains("2026-06-30");
        assertThat(content.textBody()).contains("https://youthfit.test/policies/10");
    }

    @Test
    @DisplayName("renderRecommendation: subject 에 정책 개수가 포함된다")
    void renderRecommendation_subjectIncludesCount() {
        // given
        List<Policy> policies = List.of(
                createPolicy(10L, "정책1", LocalDate.of(2026, 6, 30)),
                createPolicy(11L, "정책2", LocalDate.of(2026, 7, 15)),
                createPolicy(12L, "정책3", LocalDate.of(2026, 8, 1))
        );

        // when
        EmailContent content = renderer.renderRecommendation(policies);

        // then
        assertThat(content.subject()).contains("3");
    }

    @Test
    @DisplayName("renderRecommendation: htmlBody 에 모든 정책의 링크가 들어간다")
    void renderRecommendation_htmlBodyIncludesAllLinks() {
        // given
        List<Policy> policies = List.of(
                createPolicy(10L, "정책1", LocalDate.of(2026, 6, 30)),
                createPolicy(11L, "정책2", LocalDate.of(2026, 7, 15))
        );

        // when
        EmailContent content = renderer.renderRecommendation(policies);

        // then
        assertThat(content.htmlBody()).contains("https://youthfit.test/policies/10");
        assertThat(content.htmlBody()).contains("https://youthfit.test/policies/11");
        assertThat(content.htmlBody()).contains("정책1");
        assertThat(content.htmlBody()).contains("정책2");
    }

    @Test
    @DisplayName("htmlBody 에 알림 설정 페이지 unsubscribe 링크가 포함된다")
    void htmlBody_includesSettingsLink() {
        // given
        Policy policy = createPolicy(10L, "정책", LocalDate.of(2026, 6, 30));

        // when
        EmailContent content = renderer.renderDeadline(policy);

        // then
        assertThat(content.htmlBody()).contains("https://youthfit.test/settings/notifications");
    }

    // ── 헬퍼 ──

    private Policy createPolicy(Long id, String title, LocalDate applyEnd) {
        Policy policy = Policy.builder()
                .title(title)
                .category(Category.JOBS)
                .applyStart(LocalDate.now().minusDays(30))
                .applyEnd(applyEnd)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }

    private TemplateEngine newEngine(String mode, String suffix) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(suffix);
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
