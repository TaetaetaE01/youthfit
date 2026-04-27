package com.youthfit.guide.presentation.dto.response;

import com.youthfit.guide.application.dto.result.GuideResult;
import com.youthfit.guide.domain.model.GuideContent;

import java.time.LocalDateTime;

public record GuideResponse(
        Long policyId,
        String oneLineSummary,
        PairedDto target,
        PairedDto criteria,
        PairedDto content,
        java.util.List<PitfallDto> pitfalls,
        LocalDateTime updatedAt
) {

    public record PairedDto(java.util.List<String> items) {}

    public record PitfallDto(String text, String sourceField) {}

    public static GuideResponse from(GuideResult result) {
        GuideContent c = result.content();
        return new GuideResponse(
                result.policyId(),
                c.oneLineSummary(),
                c.target() == null ? null : new PairedDto(c.target().items()),
                c.criteria() == null ? null : new PairedDto(c.criteria().items()),
                c.content() == null ? null : new PairedDto(c.content().items()),
                c.pitfalls().stream()
                        .map(p -> new PitfallDto(p.text(), p.sourceField().name()))
                        .toList(),
                result.updatedAt()
        );
    }
}
