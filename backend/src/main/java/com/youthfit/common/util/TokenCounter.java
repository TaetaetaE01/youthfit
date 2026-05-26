package com.youthfit.common.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * OpenAI 모델용 토큰 카운터.
 * jtokkit 의 encoder 를 모델 이름 prefix 로 매핑한다.
 * 알 수 없는 모델은 o200k_base 로 안전 fallback (warn 로깅) — guide-generation 전체가
 * 모델 ID 변경 한 번에 깨지는 것을 막는다.
 */
@Component
public class TokenCounter {

    private static final Logger log = LoggerFactory.getLogger(TokenCounter.class);

    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();

    private static final Map<EncodingType, Encoding> ENCODER_CACHE = Map.of(
            EncodingType.O200K_BASE, REGISTRY.getEncoding(EncodingType.O200K_BASE),
            EncodingType.CL100K_BASE, REGISTRY.getEncoding(EncodingType.CL100K_BASE)
    );

    public int countTokens(String text, String model) {
        if (text == null || text.isEmpty()) return 0;
        Encoding encoding = encoderFor(model);
        return encoding.countTokens(text);
    }

    private Encoding encoderFor(String model) {
        if (model == null) {
            log.warn("model 이 null 이라 o200k_base 로 fallback");
            return ENCODER_CACHE.get(EncodingType.O200K_BASE);
        }
        // gpt-4o 계열 (mini, dated suffix 포함) → o200k_base
        if (model.startsWith("gpt-4o")) {
            return ENCODER_CACHE.get(EncodingType.O200K_BASE);
        }
        // legacy gpt-4-turbo / gpt-4 / gpt-3.5 / embeddings → cl100k_base
        if (model.startsWith("gpt-4") || model.startsWith("gpt-3.5")
                || model.startsWith("text-embedding-3") || model.startsWith("text-embedding-ada")) {
            return ENCODER_CACHE.get(EncodingType.CL100K_BASE);
        }
        log.warn("알 수 없는 모델 '{}' 에 대해 o200k_base 로 fallback", model);
        return ENCODER_CACHE.get(EncodingType.O200K_BASE);
    }
}
