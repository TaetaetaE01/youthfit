package com.youthfit.qna.application.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class QuestionNormalizer {

    private static final String KEY_PREFIX = "qna:answer:";

    public String normalize(String question) {
        if (question == null) return "";
        return question
                .trim()
                .toLowerCase()
                .replaceAll("[?!.]+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public String cacheKey(Long policyId, String question) {
        return KEY_PREFIX + policyId + ":" + sha256(normalize(question));
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
