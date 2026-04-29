package com.youthfit.rag.domain.service;

import com.youthfit.rag.domain.model.PolicyDocument;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocumentChunker {

    private static final int DEFAULT_MAX_CHUNK_SIZE = 500;
    private static final String PARAGRAPH_DELIMITER = "\n\n";

    private static final Pattern ATTACHMENT_HEADER = Pattern.compile(
            "===\\s*첨부\\s+attachment-id=(\\d+)\\s+name=\"([^\"]*)\"\\s*===");
    private static final String BODY_HEADER = "=== 정책 본문 ===";
    private static final Pattern PAGE_MARKER = Pattern.compile("---\\s*page=([^\\s]+)\\s*---");

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
        List<Segment> segments = splitToSegments(content.trim());

        List<PolicyDocument> documents = new ArrayList<>();
        int globalIndex = 0;
        for (Segment seg : segments) {
            for (Chunk c : chunkSegment(seg)) {
                if (c.text().isBlank()) {
                    continue;
                }
                documents.add(PolicyDocument.builder()
                        .policyId(policyId)
                        .chunkIndex(globalIndex++)
                        .content(c.text())
                        .sourceHash(sourceHash)
                        .attachmentId(seg.attachmentId())
                        .pageStart(c.pageStart())
                        .pageEnd(c.pageEnd())
                        .build());
            }
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

    /**
     * mergedContent 의 BODY_HEADER / ATTACHMENT_HEADER 위치를 기준으로
     * 본문 segment + 각 첨부 segment 로 강제 분할한다. 한 segment 안의 청크는
     * 단일 출처 (attachmentId 동일) 를 보장한다.
     */
    private List<Segment> splitToSegments(String content) {
        List<int[]> attHeaders = new ArrayList<>();
        List<Long> attIds = new ArrayList<>();
        Matcher m = ATTACHMENT_HEADER.matcher(content);
        while (m.find()) {
            attHeaders.add(new int[]{m.start(), m.end()});
            attIds.add(Long.parseLong(m.group(1)));
        }

        List<Segment> segments = new ArrayList<>();

        int firstHeaderStart = attHeaders.isEmpty() ? content.length() : attHeaders.get(0)[0];

        int bodyHeaderIdx = content.indexOf(BODY_HEADER);
        int bodyStart = bodyHeaderIdx == -1 ? 0 : bodyHeaderIdx + BODY_HEADER.length();
        if (bodyStart < firstHeaderStart) {
            String bodyText = content.substring(bodyStart, firstHeaderStart).trim();
            if (!bodyText.isBlank()) {
                segments.add(new Segment(null, bodyText));
            }
        }

        for (int i = 0; i < attHeaders.size(); i++) {
            int[] h = attHeaders.get(i);
            int segStart = h[1];
            int segEnd = (i + 1 < attHeaders.size()) ? attHeaders.get(i + 1)[0] : content.length();
            String segText = content.substring(segStart, segEnd).trim();
            if (!segText.isBlank()) {
                segments.add(new Segment(attIds.get(i), segText));
            }
        }

        return segments;
    }

    /**
     * 한 segment (본문 또는 단일 첨부) 안에서 단락 우선 분할 + maxChunkSize 길이 제한.
     * 페이지 마커 (--- page=N ---) 위치를 추적해 청크별 (pageStart, pageEnd) 추출.
     * page=null 마커는 pageStart/pageEnd 를 null 로 유지 (HWP 등 페이지 메타 없음).
     */
    private List<Chunk> chunkSegment(Segment seg) {
        String text = seg.text();
        List<PageMark> marks = collectPageMarks(text);

        List<int[]> rawChunks = paragraphAwareSplit(text);

        List<Chunk> chunks = new ArrayList<>(rawChunks.size());
        for (int[] range : rawChunks) {
            int start = range[0];
            int end = range[1];
            String chunkText = text.substring(start, end).trim();
            if (chunkText.isBlank()) {
                continue;
            }
            PageRange pr = computePageRange(marks, start, end);
            chunks.add(new Chunk(chunkText, pr.start(), pr.end()));
        }
        return chunks;
    }

    private List<PageMark> collectPageMarks(String text) {
        List<PageMark> marks = new ArrayList<>();
        Matcher pm = PAGE_MARKER.matcher(text);
        while (pm.find()) {
            String v = pm.group(1);
            Integer page = "null".equals(v) ? null : tryParseInt(v);
            marks.add(new PageMark(pm.start(), page));
        }
        return marks;
    }

    private Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 단락(\n\n) → 단락 내부 길이 분할의 2단계로 청크 경계를 잡되,
     * 결과는 원문 text 의 [start, end) offset 페어 리스트로 반환한다.
     * offset 기반이어야 페이지 마커 추적이 가능하다.
     */
    private List<int[]> paragraphAwareSplit(String text) {
        List<int[]> ranges = new ArrayList<>();
        int len = text.length();

        int paraStart = 0;
        int currentStart = -1;
        int currentEnd = -1;

        while (paraStart <= len) {
            int paraEnd = text.indexOf(PARAGRAPH_DELIMITER, paraStart);
            int boundary = (paraEnd == -1) ? len : paraEnd;

            // 현재 단락 [paraStart, boundary)
            int trimStart = paraStart;
            int trimEnd = boundary;
            while (trimStart < trimEnd && Character.isWhitespace(text.charAt(trimStart))) trimStart++;
            while (trimEnd > trimStart && Character.isWhitespace(text.charAt(trimEnd - 1))) trimEnd--;

            int paraLen = trimEnd - trimStart;
            if (paraLen > 0) {
                if (paraLen > maxChunkSize) {
                    if (currentStart != -1) {
                        ranges.add(new int[]{currentStart, currentEnd});
                        currentStart = -1;
                        currentEnd = -1;
                    }
                    splitBySize(trimStart, trimEnd, ranges);
                } else if (currentStart == -1) {
                    currentStart = trimStart;
                    currentEnd = trimEnd;
                } else if ((currentEnd - currentStart) + 2 + paraLen > maxChunkSize) {
                    ranges.add(new int[]{currentStart, currentEnd});
                    currentStart = trimStart;
                    currentEnd = trimEnd;
                } else {
                    // 같은 청크에 단락 병합 — currentEnd 만 늘림 (원본 offset 유지)
                    currentEnd = trimEnd;
                }
            }

            if (paraEnd == -1) break;
            paraStart = paraEnd + PARAGRAPH_DELIMITER.length();
        }

        if (currentStart != -1) {
            ranges.add(new int[]{currentStart, currentEnd});
        }

        return ranges;
    }

    private void splitBySize(int start, int end, List<int[]> ranges) {
        int cursor = start;
        while (cursor < end) {
            int next = Math.min(cursor + maxChunkSize, end);
            ranges.add(new int[]{cursor, next});
            cursor = next;
        }
    }

    private PageRange computePageRange(List<PageMark> marks, int start, int end) {
        Integer pageStart = null;
        Integer pageEnd = null;

        // 청크 시작 직전 가장 가까운 페이지 마커
        for (int i = marks.size() - 1; i >= 0; i--) {
            if (marks.get(i).offset() < start) {
                pageStart = marks.get(i).page();
                pageEnd = pageStart;
                break;
            }
        }
        // 청크 안에 등장하는 페이지 마커들로 range 확장
        for (PageMark mk : marks) {
            if (mk.offset() >= start && mk.offset() < end) {
                if (mk.page() == null) {
                    // page=null 은 페이지 정보 없음 의미 — 청크 범위 무효화
                    return new PageRange(null, null);
                }
                if (pageStart == null) {
                    pageStart = mk.page();
                }
                pageEnd = mk.page();
            }
        }
        if (pageStart != null && pageEnd == null) {
            pageEnd = pageStart;
        }
        return new PageRange(pageStart, pageEnd);
    }

    private record Segment(Long attachmentId, String text) {
    }

    private record Chunk(String text, Integer pageStart, Integer pageEnd) {
    }

    private record PageMark(int offset, Integer page) {
    }

    private record PageRange(Integer start, Integer end) {
    }
}
