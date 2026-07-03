package com.youthfit.eval.dataset;

/**
 * 기대 근거 스니펫 포함 판정. 청크 PK 는 재인덱싱마다 바뀌므로
 * 정답 앵커는 원문 발췌 스니펫의 정규화 포함 매칭으로 한다.
 */
public final class SnippetMatcher {

    private SnippetMatcher() {
    }

    /**
     * 연속 공백·개행을 단일 공백으로 접고 trim.
     * {@code (?U)} (UNICODE_CHARACTER_CLASS) 로 {@code \s} 를 {@code \p{IsWhite_Space}} 로 확장해
     * 비분리 공백(U+00A0)·전각 공백(U+3000) 등 정부 사이트 원문에 흔한 유니코드 공백도 매칭한다.
     */
    public static String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("(?U)\\s+", " ").trim();
    }

    public static boolean containsSnippet(String content, String snippet) {
        String normalizedSnippet = normalize(snippet);
        if (normalizedSnippet.isEmpty()) return false;
        return normalize(content).contains(normalizedSnippet);
    }
}
