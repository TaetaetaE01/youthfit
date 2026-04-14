package com.youthfit.guide.application.port;

public interface GuideLlmProvider {

    String generateGuideSummary(String policyTitle, String documentContent);
}
