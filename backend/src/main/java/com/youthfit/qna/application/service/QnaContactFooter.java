package com.youthfit.qna.application.service;

public final class QnaContactFooter {

    private static final String SEPARATOR = "\n\n---\n\n";
    private static final String PREFIX = "📞 문의: ";

    private QnaContactFooter() {
    }

    /**
     * 답변에 첨부할 푸터 문자열을 만든다. 첨부할 조건이 아니면 null을 반환한다.
     * (cache와 SSE 송출 양쪽이 같은 출력을 쓰도록 단일 소스로 둔다)
     */
    public static String formatFooter(String organization, String contact, boolean isFallbackAnswer) {
        if (isFallbackAnswer) return null;
        if (isBlank(organization) || isBlank(contact)) return null;
        return SEPARATOR + PREFIX + organization + " · " + contact;
    }

    public static String appendIfPossible(String answer, String organization, String contact, boolean isFallbackAnswer) {
        String footer = formatFooter(organization, contact, isFallbackAnswer);
        return footer == null ? answer : answer + footer;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
