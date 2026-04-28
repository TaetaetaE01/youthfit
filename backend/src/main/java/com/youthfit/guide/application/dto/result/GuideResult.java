package com.youthfit.guide.application.dto.result;

import com.youthfit.guide.domain.model.Guide;
import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuideHighlight;

import java.time.LocalDateTime;
import java.util.List;

public record GuideResult(
        Long id,
        Long policyId,
        GuideContent content,
        List<GuideHighlightDto> highlights,
        String sourceHash,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static GuideResult from(Guide guide) {
        GuideContent c = guide.getContent();
        return new GuideResult(
                guide.getId(),
                guide.getPolicyId(),
                c,
                c.highlights().stream().map(GuideHighlightDto::from).toList(),
                guide.getSourceHash(),
                guide.getCreatedAt(),
                guide.getUpdatedAt()
        );
    }

    public record GuideHighlightDto(String text, String sourceField) {
        public static GuideHighlightDto from(GuideHighlight h) {
            return new GuideHighlightDto(h.text(), h.sourceField().name());
        }
    }
}
