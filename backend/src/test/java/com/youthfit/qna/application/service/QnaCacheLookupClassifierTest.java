package com.youthfit.qna.application.service;

import com.youthfit.qna.application.dto.result.CachedAnswer;
import com.youthfit.qna.application.port.dto.SemanticLookupMatch;
import com.youthfit.qna.application.port.dto.SemanticLookupResult;
import com.youthfit.qna.domain.model.LookupResultType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QnaCacheLookupClassifierTest {

    QnaCacheLookupClassifier classifier = new QnaCacheLookupClassifier();

    @Test
    void miss_closest_없으면_MISS() {
        var result = SemanticLookupResult.miss();
        assertThat(classifier.classify(result)).isEqualTo(LookupResultType.MISS);
    }

    @Test
    void belowThreshold_closest_있고_cached없으면_BELOW_THRESHOLD() {
        var result = SemanticLookupResult.belowThreshold(
                new SemanticLookupMatch(1L, BigDecimal.valueOf(0.85), BigDecimal.valueOf(0.30))
        );
        assertThat(classifier.classify(result)).isEqualTo(LookupResultType.BELOW_THRESHOLD);
    }

    @Test
    void hit_closest_와_cachedAnswer_둘다있으면_HIT() {
        var match = new SemanticLookupMatch(1L, BigDecimal.valueOf(0.92), BigDecimal.valueOf(0.15));
        var cachedAnswer = new CachedAnswer("테스트 답변", List.of(), List.of(), Instant.now());
        var result = SemanticLookupResult.hit(match, cachedAnswer);
        assertThat(classifier.classify(result)).isEqualTo(LookupResultType.HIT);
    }
}
