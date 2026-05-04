package com.youthfit.user.application.service;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.application.dto.result.EmailContent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Locale;

@Component
public class NotificationEmailRenderer {

    private static final Locale KOREAN = Locale.KOREAN;

    private final TemplateEngine htmlEngine;
    private final TemplateEngine textEngine;
    private final String baseUrl;

    public NotificationEmailRenderer(@Qualifier("emailHtmlTemplateEngine") TemplateEngine htmlEngine,
                                     @Qualifier("emailTextTemplateEngine") TemplateEngine textEngine,
                                     @Value("${youthfit.email.base-url}") String baseUrl) {
        this.htmlEngine = htmlEngine;
        this.textEngine = textEngine;
        this.baseUrl = baseUrl;
    }

    public EmailContent renderDeadline(Policy policy) {
        Context ctx = new Context(KOREAN);
        ctx.setVariable("policy", policy);
        ctx.setVariable("baseUrl", baseUrl);

        String subject = "[YouthFit] " + policy.getTitle() + " 마감 임박 알림";
        String html = htmlEngine.process("email/deadline", ctx);
        String text = textEngine.process("email/deadline", ctx);
        return new EmailContent(subject, html, text);
    }

    public EmailContent renderRecommendation(List<Policy> policies) {
        Context ctx = new Context(KOREAN);
        ctx.setVariable("policies", policies);
        ctx.setVariable("count", policies.size());
        ctx.setVariable("baseUrl", baseUrl);

        String subject = "[YouthFit] 이번 주 당신에게 맞을 수 있는 정책 " + policies.size() + "개";
        String html = htmlEngine.process("email/recommendation", ctx);
        String text = textEngine.process("email/recommendation", ctx);
        return new EmailContent(subject, html, text);
    }
}
