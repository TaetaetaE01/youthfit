package com.youthfit.policy.application.port;

import com.youthfit.policy.domain.model.IncomeBracketReference;
import java.util.Optional;

public interface IncomeBracketReferenceLoader {
    Optional<IncomeBracketReference> findByYear(int year);
    IncomeBracketReference findLatest();
}
