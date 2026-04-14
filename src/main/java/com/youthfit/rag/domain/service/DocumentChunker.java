package com.youthfit.rag.domain.service;

import com.youthfit.rag.domain.model.PolicyDocument;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public class DocumentChunker {

    private static final int DEFAULT_MAX_CHUNK_SIZE = 500;
    private static final String PARAGRAPH_DELIMITER = "\n\n";
    private static final String SENTENCE_DELIMITER = ". ";

    private final int maxChunkSize;

    public DocumentChunker() {
        this.maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;
    }

    public DocumentChunker(int maxChunkSize) {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("maxChunkSize must be positive");
        }
        this.maxChunkSize = maxChunkSize;
    }

    public List<PolicyDocument> chunk(Long policyId, String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        String sourceHash = computeHash(content);
        List<String> chunks = splitIntoChunks(content.trim());

        List<PolicyDocument> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            documents.add(PolicyDocument.builder()
                    .policyId(policyId)
                    .chunkIndex(i)
                    .content(chunks.get(i))
                    .sourceHash(sourceHash)
                    .build());
        }
        return documents;
    }

    public String computeHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private List<String> splitIntoChunks(String content) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = content.split(PARAGRAPH_DELIMITER);

        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.length() > maxChunkSize) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString().trim());
                    current = new StringBuilder();
                }
                splitLongParagraph(trimmed, chunks);
            } else if (current.length() + trimmed.length() + 2 > maxChunkSize) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString().trim());
                    current = new StringBuilder();
                }
                current.append(trimmed);
            } else {
                if (!current.isEmpty()) {
                    current.append("\n\n");
                }
                current.append(trimmed);
            }
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }

        return chunks;
    }

    private void splitLongParagraph(String paragraph, List<String> chunks) {
        String[] sentences = paragraph.split("(?<=\\. )|(?<=\\.$)|(?<=！)|(?<=\\?)");
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.length() > maxChunkSize) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString().trim());
                    current = new StringBuilder();
                }
                splitBySize(trimmed, chunks);
            } else if (current.length() + trimmed.length() + 1 > maxChunkSize) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString().trim());
                    current = new StringBuilder();
                }
                current.append(trimmed);
            } else {
                if (!current.isEmpty()) {
                    current.append(" ");
                }
                current.append(trimmed);
            }
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }
    }

    private void splitBySize(String text, List<String> chunks) {
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChunkSize, text.length());
            chunks.add(text.substring(start, end).trim());
            start = end;
        }
    }
}
