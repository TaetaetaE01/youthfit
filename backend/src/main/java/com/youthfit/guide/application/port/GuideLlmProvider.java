package com.youthfit.guide.application.port;

import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.domain.model.GuideContent;

import java.util.List;

public interface GuideLlmProvider {

    GuideContent generateGuide(GuideGenerationInput input);

    GuideContent regenerateWithFeedback(GuideGenerationInput input, List<String> feedbackMessages);

    /**
     * 큰 정책의 청크 그룹 하나에 대해 부분 가이드를 생성한다.
     * 응답은 풀 GuideContent 형식 (validator/schema 재사용).
     * 입력 input.chunks() 는 한 그룹의 청크들만 들어 있어야 한다.
     */
    GuideContent generatePartialGuide(GuideGenerationInput input);

    /**
     * 여러 부분 가이드를 통합해 최종 가이드를 생성한다.
     * 입력 input 의 chunks 는 비어 있어도 되며, partials 가 주된 입력이다.
     */
    GuideContent mergePartialGuides(GuideGenerationInput input, List<GuideContent> partials);
}
