package com.youthfit.guide.application.port;

import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.domain.model.GuideContent;

import java.util.List;

public interface GuideLlmProvider {

    GuideContent generateGuide(GuideGenerationInput input);

    GuideContent regenerateWithFeedback(GuideGenerationInput input, List<String> feedbackMessages);
}
