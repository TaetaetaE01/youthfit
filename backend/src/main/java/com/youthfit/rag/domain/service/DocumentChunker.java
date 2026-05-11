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
    private static final String CHUNKER_VERSION = "v2";

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
            List<Chunk> segChunks = chunkSegment(seg);
            List<Chunk> withOverlap = applyOverlap(segChunks);
            for (Chunk c : withOverlap) {
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
            String input = CHUNKER_VERSION + ":" + content;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
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

        List<TableBlock> tableBlocks = identifyTableBlocks(text);
        List<int[]> rawChunks = chunkWithTableBlocks(text, tableBlocks);

        List<Chunk> chunks = new ArrayList<>(rawChunks.size());
        for (int[] range : rawChunks) {
            int start = range[0];
            int end = range[1];
            String chunkText = text.substring(start, end).trim();
            if (chunkText.isBlank()) {
                continue;
            }
            String finalText = applyHeaderIfNeeded(chunkText, tableBlocks, start, end);
            PageRange pr = computePageRange(marks, start, end);
            chunks.add(new Chunk(finalText, pr.start(), pr.end()));
        }
        return chunks;
    }

    private static final int OVERLAP_CHARS = 80;
    private static final int OVERLAP_BACKTRACK_LIMIT = 20;

    /**
     * 일반 평문 청크에 ~80자 overlap 을 prepend. 표 청크(헤더로 시작)는 스킵.
     */
    private List<Chunk> applyOverlap(List<Chunk> chunks) {
        if (chunks.size() < 2) return chunks;

        List<Chunk> result = new ArrayList<>(chunks.size());
        result.add(chunks.get(0));
        for (int i = 1; i < chunks.size(); i++) {
            Chunk prev = chunks.get(i - 1);
            Chunk cur = chunks.get(i);

            if (looksLikeTableChunk(cur.text())) {
                result.add(cur);
                continue;
            }

            String overlap = computeOverlapPrefix(prev.text());
            if (overlap.isEmpty()) {
                result.add(cur);
            } else {
                result.add(new Chunk(overlap + "\n" + cur.text(), cur.pageStart(), cur.pageEnd()));
            }
        }
        return result;
    }

    private boolean looksLikeTableChunk(String text) {
        String[] lines = text.split("\n", 3);
        if (lines.length < 2) return false;
        // 1) 헤더 prepend 된 표: line 0 = 비-번호 헤더, line 1 = 번호 행
        // 2) 헤더 없는 표 (헤더 거부됨): line 0 = 번호 행, line 1 = 번호 행
        if (NUMBER_ROW.matcher(lines[0]).find() && NUMBER_ROW.matcher(lines[1]).find()) {
            return true;
        }
        return !NUMBER_ROW.matcher(lines[0]).find()
                && NUMBER_ROW.matcher(lines[1]).find();
    }

    private String computeOverlapPrefix(String prevText) {
        if (prevText.length() <= OVERLAP_CHARS) return prevText;
        int start = prevText.length() - OVERLAP_CHARS;
        int backtrack = 0;
        while (start < prevText.length() && backtrack < OVERLAP_BACKTRACK_LIMIT) {
            char ch = prevText.charAt(start);
            if (Character.isWhitespace(ch) || ".,!?;:".indexOf(ch) >= 0) {
                start++;
                break;
            }
            start++;
            backtrack++;
        }
        if (start >= prevText.length()) return "";
        return prevText.substring(start);
    }

    private static final Pattern NUMBER_ROW = Pattern.compile(
            "^(\\d+\\s+|\\d+\\.\\s+|[①②③④⑤⑥⑦⑧⑨⑩])");
    private static final int MIN_TABLE_ROWS = 3;
    private static final int HEADER_PLAINTEXT_MIN_LEN = 20;
    private static final Pattern PLAINTEXT_END = Pattern.compile(".*[.?!]\\s*$");

    private List<TableBlock> identifyTableBlocks(String text) {
        List<TableBlock> blocks = new ArrayList<>();
        int len = text.length();
        int cursor = 0;
        Integer prevLineStart = null;
        Integer prevLineEnd = null;
        int runStart = -1;
        int runCount = 0;

        while (cursor <= len) {
            int lineEnd = text.indexOf('\n', cursor);
            int finish = (lineEnd == -1) ? len : lineEnd;
            String line = text.substring(cursor, finish);

            if (NUMBER_ROW.matcher(line).find()) {
                if (runStart == -1) {
                    runStart = cursor;
                }
                runCount++;
            } else {
                if (runCount >= MIN_TABLE_ROWS) {
                    String header = pickHeader(text, prevLineStart, prevLineEnd);
                    int blockStart = (header != null && prevLineStart != null) ? prevLineStart : runStart;
                    blocks.add(new TableBlock(blockStart, cursor - 1, header));
                }
                runStart = -1;
                runCount = 0;
                prevLineStart = cursor;
                prevLineEnd = finish;
            }

            if (lineEnd == -1) {
                if (runCount >= MIN_TABLE_ROWS) {
                    String header = pickHeader(text, prevLineStart, prevLineEnd);
                    int blockStart = (header != null && prevLineStart != null) ? prevLineStart : runStart;
                    blocks.add(new TableBlock(blockStart, finish, header));
                }
                break;
            }
            cursor = lineEnd + 1;
        }
        return blocks;
    }

    private String pickHeader(String text, Integer prevStart, Integer prevEnd) {
        if (prevStart == null) return null;
        String candidate = text.substring(prevStart, prevEnd).trim();
        if (candidate.isEmpty()) return null;
        if (candidate.length() > HEADER_PLAINTEXT_MIN_LEN
                && PLAINTEXT_END.matcher(candidate).matches()) {
            return null;
        }
        return candidate;
    }

    private List<int[]> chunkWithTableBlocks(String text, List<TableBlock> blocks) {
        if (blocks.isEmpty()) {
            return paragraphAwareSplit(text);
        }

        List<int[]> ranges = new ArrayList<>();
        int cursor = 0;
        for (TableBlock b : blocks) {
            if (cursor < b.start) {
                List<int[]> subRanges = paragraphAwareSplit(text.substring(cursor, b.start));
                final int offset = cursor;
                for (int[] r : subRanges) {
                    ranges.add(new int[]{r[0] + offset, r[1] + offset});
                }
            }
            List<int[]> tableRanges = new ArrayList<>();
            splitByLines(text, b.start, b.end, tableRanges);
            ranges.addAll(tableRanges);
            cursor = b.end;
        }
        if (cursor < text.length()) {
            List<int[]> subRanges = paragraphAwareSplit(text.substring(cursor));
            final int offset = cursor;
            for (int[] r : subRanges) {
                ranges.add(new int[]{r[0] + offset, r[1] + offset});
            }
        }
        return ranges;
    }

    private String applyHeaderIfNeeded(String chunkText, List<TableBlock> blocks, int start, int end) {
        for (TableBlock b : blocks) {
            if (start >= b.start && end <= b.end && b.header != null) {
                if (chunkText.startsWith(b.header)) {
                    return chunkText;
                }
                return b.header + "\n" + chunkText;
            }
        }
        return chunkText;
    }

    private record TableBlock(int start, int end, String header) {}

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
                    splitByLines(text, trimStart, trimEnd, ranges);
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

    /**
     * 단락(\n\n) 내부가 maxChunkSize 보다 긴 경우 줄(\n) 단위로 누적 분할.
     * 단일 줄 자체가 maxChunkSize 를 넘는 극단 케이스만 글자 단위 cut (last-resort).
     * 결과는 원문 text 의 [start, end) offset 페어 리스트로 반환한다.
     */
    private void splitByLines(String text, int start, int end, List<int[]> ranges) {
        int cursor = start;
        int chunkStart = -1;
        int chunkEnd = -1;

        while (cursor < end) {
            int lineEnd = text.indexOf('\n', cursor);
            int lineFinish = (lineEnd == -1 || lineEnd >= end) ? end : lineEnd;
            int lineLen = lineFinish - cursor;

            if (lineLen > maxChunkSize) {
                if (chunkStart != -1) {
                    ranges.add(new int[]{chunkStart, chunkEnd});
                    chunkStart = -1;
                    chunkEnd = -1;
                }
                int c = cursor;
                while (c < lineFinish) {
                    int next = Math.min(c + maxChunkSize, lineFinish);
                    ranges.add(new int[]{c, next});
                    c = next;
                }
            } else if (chunkStart == -1) {
                chunkStart = cursor;
                chunkEnd = lineFinish;
            } else if ((chunkEnd - chunkStart) + 1 + lineLen > maxChunkSize) {
                ranges.add(new int[]{chunkStart, chunkEnd});
                chunkStart = cursor;
                chunkEnd = lineFinish;
            } else {
                chunkEnd = lineFinish;
            }

            if (lineEnd == -1 || lineEnd >= end) break;
            cursor = lineEnd + 1;
        }

        if (chunkStart != -1) {
            ranges.add(new int[]{chunkStart, chunkEnd});
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
