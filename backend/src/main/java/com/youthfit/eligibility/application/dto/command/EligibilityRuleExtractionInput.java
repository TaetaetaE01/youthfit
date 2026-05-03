package com.youthfit.eligibility.application.dto.command;

import java.util.List;

public record EligibilityRuleExtractionInput(
        Long policyId,
        String title,
        String summary,
        String supportTarget,
        String selectionCriteria,
        String supportContent,
        String body,
        List<RuleExtractionChunk> attachmentChunks
) {

    public String combinedSourceText() {
        StringBuilder sb = new StringBuilder();
        sb.append("[정책 메타]\n");
        sb.append("policyId: ").append(policyId).append("\n");
        sb.append("title: ").append(safe(title)).append("\n");
        sb.append("summary: ").append(safe(summary)).append("\n\n");

        sb.append("[원문 - 지원 대상]\n").append(safe(supportTarget)).append("\n\n");
        sb.append("[원문 - 선정 기준]\n").append(safe(selectionCriteria)).append("\n\n");
        sb.append("[원문 - 지원 내용]\n").append(safe(supportContent)).append("\n\n");
        sb.append("[원문 - 본문]\n").append(safe(body)).append("\n\n");

        sb.append("[원문 - 첨부 청크]\n");
        for (RuleExtractionChunk c : attachmentChunks) {
            String pages = c.pageStart() == null ? "" : " pages=" + c.pageStart() + "-" + c.pageEnd();
            sb.append("[chunk attachment-id=").append(c.attachmentId()).append(pages).append("]\n");
            sb.append(safe(c.content())).append("\n\n");
        }
        return sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
