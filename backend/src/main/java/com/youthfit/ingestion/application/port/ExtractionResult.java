package com.youthfit.ingestion.application.port;

import com.youthfit.policy.domain.model.SkipReason;

public sealed interface ExtractionResult {

    record Success(String text) implements ExtractionResult {}
    record Skipped(SkipReason reason) implements ExtractionResult {}
    record Failed(String error) implements ExtractionResult {}

    static ExtractionResult success(String text) { return new Success(text); }
    static ExtractionResult skipped(SkipReason reason) { return new Skipped(reason); }
    static ExtractionResult failed(String error) { return new Failed(error); }
}
