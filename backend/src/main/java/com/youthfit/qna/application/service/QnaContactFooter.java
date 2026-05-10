package com.youthfit.qna.application.service;

public final class QnaContactFooter {

    private static final String SEPARATOR = "\n\n---\n\n";
    private static final String PREFIX = "📞 문의: ";

    private QnaContactFooter() {
    }

    public static String appendIfPossible(String answer, String organization, String contact, boolean isFallbackAnswer) {
        if (isFallbackAnswer) return answer;
        if (isBlank(organization) || isBlank(contact)) return answer;
        return answer + SEPARATOR + PREFIX + organization + " · " + contact;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
