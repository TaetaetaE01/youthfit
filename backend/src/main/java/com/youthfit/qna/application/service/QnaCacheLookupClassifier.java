package com.youthfit.qna.application.service;

import com.youthfit.qna.application.port.dto.SemanticLookupResult;
import com.youthfit.qna.domain.model.LookupResultType;
import org.springframework.stereotype.Component;

@Component
public class QnaCacheLookupClassifier {

    public LookupResultType classify(SemanticLookupResult result) {
        if (result.closest().isEmpty()) return LookupResultType.MISS;
        if (result.cachedAnswer().isPresent()) return LookupResultType.HIT;
        return LookupResultType.BELOW_THRESHOLD;
    }
}
