package com.youthfit.guide.presentation.dto.response;

import com.youthfit.guide.application.dto.result.GuideResult;
import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuidePairedSection;

import java.time.LocalDateTime;
import java.util.List;

public record GuideResponse(
        Long policyId,
        String oneLineSummary,
        List<HighlightDto> highlights,
        PairedDto target,
        PairedDto criteria,
        PairedDto content,
        List<PitfallDto> pitfalls,
        LocalDateTime updatedAt
) {

    public record PairedDto(List<GroupDto> groups) {}

    public record GroupDto(String label, List<String> items) {}

    public record AttachmentRefDto(Long attachmentId, Integer pageStart, Integer pageEnd) {
        public static AttachmentRefDto from(com.youthfit.guide.domain.model.AttachmentRef r) {
            return r == null ? null
                    : new AttachmentRefDto(r.attachmentId(), r.pageStart(), r.pageEnd());
        }
    }

    public record PitfallDto(String text, String sourceField, AttachmentRefDto attachmentRef) {}

    public record HighlightDto(String text, String sourceField, AttachmentRefDto attachmentRef) {}

    public static GuideResponse from(GuideResult result) {
        GuideContent c = result.content();
        return new GuideResponse(
                result.policyId(),
                c.oneLineSummary(),
                c.highlights().stream()
                        .map(h -> new HighlightDto(h.text(), h.sourceField().name(),
                                AttachmentRefDto.from(h.attachmentRef())))
                        .toList(),
                toPairedDto(c.target()),
                toPairedDto(c.criteria()),
                toPairedDto(c.content()),
                c.pitfalls().stream()
                        .map(p -> new PitfallDto(p.text(), p.sourceField().name(),
                                AttachmentRefDto.from(p.attachmentRef())))
                        .toList(),
                result.updatedAt()
        );
    }

    private static PairedDto toPairedDto(GuidePairedSection section) {
        if (section == null) return null;
        List<GroupDto> groups = section.groups().stream()
                .map(g -> new GroupDto(g.label(), g.items()))
                .toList();
        return new PairedDto(groups);
    }
}
