package com.youthfit.guide.application.service;

import com.youthfit.guide.application.service.GuideValidator.ValidationReport;
import com.youthfit.guide.domain.model.AttachmentRef;
import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuideGroup;
import com.youthfit.guide.domain.model.GuideHighlight;
import com.youthfit.guide.domain.model.GuidePairedSection;
import com.youthfit.guide.domain.model.GuidePitfall;
import com.youthfit.guide.domain.model.GuideSourceField;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GuideValidatorTest {

    GuideValidator validator = new GuideValidator();

    private GuidePairedSection flat(String... items) {
        return new GuidePairedSection(List.of(new GuideGroup(null, List.of(items))));
    }

    @Test
    void 원문에_금액이_풀이에_보존되면_통과() {
        String originalText = "월세 60만원 이하 주택 거주 청년";
        GuideContent content = new GuideContent(
                "월세 지원",
                List.of(),
                flat("월세 60만원 이하 주택 거주자"),
                null, null, List.of());

        assertThat(validator.findMissingNumericTokens(originalText, content)).isEmpty();
    }

    @Test
    void 원문에_있던_금액이_풀이에_없으면_누락_검출() {
        String originalText = "월세 60만원 이하 주택, 만 19세 이상";
        GuideContent content = new GuideContent(
                "월세 지원",
                List.of(),
                flat("청년 월세 지원"),
                null, null, List.of());

        List<String> missing = validator.findMissingNumericTokens(originalText, content);
        assertThat(missing).contains("60만원", "19세");
    }

    @Test
    void 친근체_검출() {
        GuideContent content = new GuideContent(
                "이 정책은 청년에게 도움이 돼요.",
                List.of(),
                flat("받을 수 있어요"),
                null, null, List.of());

        assertThat(validator.containsFriendlyTone(content)).isTrue();
    }

    @Test
    void 명사형_단정형은_친근체_아님() {
        GuideContent content = new GuideContent(
                "만 19~34세 청년 월세 지원",
                List.of(),
                flat("본인 명의 계약자"),
                null, null, List.of());

        assertThat(validator.containsFriendlyTone(content)).isFalse();
    }

    @Test
    void 그룹_분리_위반_차상위와_일반공급_섞임() {
        GuidePairedSection criteria = pairedSingleGroup(null, List.of(
                "차상위계층 이하인 경우",
                "일반공급 대상자인 경우"
        ));
        GuideContent content = content(criteria);

        ValidationReport report = validator.validate(content, Set.of());

        assertThat(report.hasGroupMixViolation()).isTrue();
    }

    @Test
    void highlights_부족_2개() {
        GuideContent content = new GuideContent(
                "요약",
                List.of(
                        new GuideHighlight("a", GuideSourceField.BODY),
                        new GuideHighlight("b", GuideSourceField.BODY)
                ),
                null, null, null, List.of());

        ValidationReport report = validator.validate(content, Set.of());

        assertThat(report.hasInsufficientHighlights()).isTrue();
    }

    @Test
    void highlights_충분_3개() {
        GuideContent content = new GuideContent(
                "요약",
                List.of(
                        new GuideHighlight("a", GuideSourceField.BODY),
                        new GuideHighlight("b", GuideSourceField.BODY),
                        new GuideHighlight("c", GuideSourceField.BODY)
                ),
                null, null, null, List.of());

        ValidationReport report = validator.validate(content, Set.of());

        assertThat(report.hasInsufficientHighlights()).isFalse();
    }

    @Test
    void filterInvalidSourceFields_빈_입력_필드_가리키는_pitfall_제거() {
        GuidePitfall valid = new GuidePitfall("월세 60만원 초과 제외", GuideSourceField.SUPPORT_TARGET);
        GuidePitfall invalid = new GuidePitfall("선정기준 텍스트", GuideSourceField.SELECTION_CRITERIA);
        List<GuidePitfall> filtered = validator.filterInvalidSourceFields(
                List.of(valid, invalid),
                sourceFieldsSet("SUPPORT_TARGET"),
                GuidePitfall::sourceField
        );

        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0)).isEqualTo(valid);
    }

    @Test
    void givenAttachmentSourceFieldWithNullRef_whenValidate_thenInvalid() {
        GuideContent content = guideContentWith(
                List.of(new GuideHighlight("text", GuideSourceField.ATTACHMENT, null)),
                List.of());

        ValidationReport report = validator.validate(content, Set.of(12L));

        assertThat(report.hasInvalidAttachmentRef()).isTrue();
        assertThat(report.hasRetryTrigger()).isTrue();
    }

    @Test
    void givenAttachmentRefToUnknownId_whenValidate_thenInvalid() {
        GuideContent content = guideContentWith(
                List.of(new GuideHighlight("text", GuideSourceField.ATTACHMENT,
                        new AttachmentRef(99L, 1, 1))),
                List.of());

        ValidationReport report = validator.validate(content, Set.of(12L, 13L));

        assertThat(report.hasInvalidAttachmentRef()).isTrue();
    }

    @Test
    void givenNonAttachmentWithRef_whenValidate_thenInvalid() {
        GuideContent content = guideContentWith(
                List.of(new GuideHighlight("text", GuideSourceField.SUPPORT_TARGET,
                        new AttachmentRef(12L, 1, 1))),
                List.of());

        ValidationReport report = validator.validate(content, Set.of(12L));

        assertThat(report.hasInvalidAttachmentRef()).isTrue();
    }

    @Test
    void givenValidAttachmentRef_whenValidate_thenOk() {
        GuideContent content = guideContentWith(
                List.of(
                        new GuideHighlight("hi-1", GuideSourceField.SUPPORT_TARGET, null),
                        new GuideHighlight("hi-2", GuideSourceField.ATTACHMENT,
                                new AttachmentRef(12L, 35, 37)),
                        new GuideHighlight("hi-3", GuideSourceField.BODY, null)
                ),
                List.of());

        ValidationReport report = validator.validate(content, Set.of(12L));

        assertThat(report.hasInvalidAttachmentRef()).isFalse();
    }

    private GuideContent guideContentWith(List<GuideHighlight> hi, List<GuidePitfall> pi) {
        return new GuideContent(
                "summary", hi,
                null, null, null,
                pi);
    }

    private Set<GuideSourceField> sourceFieldsSet(String... names) {
        return Arrays.stream(names).map(GuideSourceField::valueOf)
                .collect(Collectors.toSet());
    }

    private GuidePairedSection pairedSingleGroup(String label, List<String> items) {
        return new GuidePairedSection(List.of(new GuideGroup(label, items)));
    }

    private GuideContent content(GuidePairedSection criteria) {
        return new GuideContent("요약",
                List.of(
                        new GuideHighlight("a", GuideSourceField.BODY),
                        new GuideHighlight("b", GuideSourceField.BODY),
                        new GuideHighlight("c", GuideSourceField.BODY)),
                null, criteria, null, List.of());
    }
}
