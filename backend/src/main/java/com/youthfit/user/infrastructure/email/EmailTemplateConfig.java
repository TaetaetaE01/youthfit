package com.youthfit.user.infrastructure.email;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Configuration
public class EmailTemplateConfig {

    @Bean("emailHtmlTemplateEngine")
    public TemplateEngine emailHtmlTemplateEngine() {
        return buildEngine(TemplateMode.HTML, ".html");
    }

    @Bean("emailTextTemplateEngine")
    public TemplateEngine emailTextTemplateEngine() {
        return buildEngine(TemplateMode.TEXT, ".txt");
    }

    private TemplateEngine buildEngine(TemplateMode mode, String suffix) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(suffix);
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
