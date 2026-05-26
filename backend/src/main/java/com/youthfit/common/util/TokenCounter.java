package com.youthfit.common.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * OpenAI 모델용 토큰 카운터.
 * jtokkit 의 encoder 를 모델 이름별로 매핑한다.
 */
@Component
public class TokenCounter {

    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();

    /** o200k_base 를 쓰는 모델들 (gpt-4o 계열 + mini). */
    private static final Set<String> O200K_MODELS = Set.of(
            "gpt-4o", "gpt-4o-mini", "gpt-4o-2024-08-06"
    );

    /** cl100k_base 를 쓰는 모델들 (legacy gpt-4-turbo, gpt-3.5, embeddings). */
    private static final Set<String> CL100K_MODELS = Set.of(
            "gpt-4-turbo", "gpt-3.5-turbo",
            "text-embedding-3-small", "text-embedding-3-large", "text-embedding-ada-002"
    );

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
        if (O200K_MODELS.contains(model)) {
            return ENCODER_CACHE.get(EncodingType.O200K_BASE);
        }
        if (CL100K_MODELS.contains(model)) {
            return ENCODER_CACHE.get(EncodingType.CL100K_BASE);
        }
        throw new IllegalArgumentException("Unsupported model for token counting: " + model);
    }
}
