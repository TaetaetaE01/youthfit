package com.youthfit.guide.application.port;

import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.domain.model.GuideContent;

public interface GuideLlmProvider {

    GuideContent generateGuide(GuideGenerationInput input);
}
