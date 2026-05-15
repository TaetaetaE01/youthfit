package com.youthfit.rag.domain.service;

import com.youthfit.policy.domain.model.PolicyEnrichment;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.model.PolicyDocumentSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocumentChunker {

    // 청킹 관련 상수
    private static final int DEFAULT_MAX_CHUNK_SIZE = 500;
    private static final String PARAGRAPH_DELIMITER = "\n\n";
    private static final String CHUNKER_VERSION = "v2";

    // 표 인식 관련 상수
    private static final Pattern NUMBER_ROW = Pattern.compile(
            "^(\\d+\\s+|\\d+\\.\\s+|[①②③④⑤⑥⑦⑧⑨⑩])");
    private static final int MIN_TABLE_ROWS = 3;
    private static final int HEADER_PLAINTEXT_MIN_LEN = 20;
    private static final Pattern PLAINTEXT_END = Pattern.compile(".*[.?!]\\s*$");

    // 오버랩 관련 상수
    private static final int OVERLAP_CHARS = 80;
    private static final int OVERLAP_BACKTRACK_LIMIT = 20;

    // 첨부/페이지 마커
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
                        .source(seg.attachmentId() == null ? PolicyDocumentSource.BODY : PolicyDocumentSource.ATTACHMENT)
                        .build());
            }
        }
        return documents;
    }

    /**
     * 기본 chunk 처리 후, PolicyEnrichment 가 노출 가능(isExposable)하면
     * Sections 의 non-null 필드를 각각 별도 청크로 추가한다.
     *
     * 각 enrichment 청크는 "[자동수집-섹션명] ..." 형태의 텍스트 prefix 로 출처를 표기한다.
     * 이 방식은 PolicyDocument 스키마 변경 없이도 검색 인용 시 출처 구분이 가능하다.
     *
     * @param policyId   정책 ID
     * @param content    기존 정책 본문 + 첨부 merged content
     * @param enrichment nullable — null 이면 기존 chunk() 와 동일
     * @return 기본 청크 + enrichment 청크 (globalIndex 연속)
     */
    public List<PolicyDocument> chunkWithEnrichment(Long policyId, String content,
                                                     PolicyEnrichment enrichment) {
        List<PolicyDocument> base = chunk(policyId, content);

        if (enrichment == null || !enrichment.isExposable() || enrichment.sections() == null) {
            return base;
        }

        PolicyEnrichment.Sections sections = enrichment.sections();

        // 섹션명 → 텍스트 매핑 (null 제외)
        Map<String, String> sectionEntries = new java.util.LinkedHashMap<>();
        if (sections.supportTarget() != null && !sections.supportTarget().isBlank()) {
            sectionEntries.put("지원대상", sections.supportTarget());
        }
        if (sections.supportContent() != null && !sections.supportContent().isBlank()) {
            sectionEntries.put("지원내용", sections.supportContent());
        }
        if (sections.applyMethod() != null && !sections.applyMethod().isBlank()) {
            sectionEntries.put("신청방법", sections.applyMethod());
        }
        if (sections.requiredDocuments() != null && !sections.requiredDocuments().isBlank()) {
            sectionEntries.put("제출서류", sections.requiredDocuments());
        }
        if (sections.deadlineNote() != null && !sections.deadlineNote().isBlank()) {
            sectionEntries.put("마감안내", sections.deadlineNote());
        }

        if (sectionEntries.isEmpty()) {
            return base;
        }

        List<PolicyDocument> result = new ArrayList<>(base);
        int globalIndex = base.size();
        String sourceHash = computeHash(content);

        for (Map.Entry<String, String> entry : sectionEntries.entrySet()) {
            String enrichedContent = "[자동수집-" + entry.getKey() + "] " + entry.getValue();
            result.add(PolicyDocument.builder()
                    .policyId(policyId)
                    .chunkIndex(globalIndex++)
                    .content(enrichedContent)
                    .sourceHash(sourceHash)
                    .source(PolicyDocumentSource.BODY)
                    .build());
        }

        return result;
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
     *
     * 진입 시 expandParagraphTables 로 단락형 표(한 줄에 다 들어간 표) 를 줄 단위로 펼치고
     * 자연어 prefix("표 항목: ...") 를 첨가한다. 이렇게 해야 표 청크가 자연어 query 의
     * embedding 과 거리상 가까워져 retrieval 이 회수할 수 있다.
     */
    private List<Chunk> chunkSegment(Segment seg) {
        String text = expandParagraphTables(seg.text());
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

    /**
     * 일반 평문 청크에 ~80자 overlap 을 prepend. 표 청크(헤더로 시작)는 스킵.
     * prev 가 표 청크인 경우도 스킵: 표 내용이 다음 평문 청크로 새지 않도록.
     */
    private List<Chunk> applyOverlap(List<Chunk> chunks) {
        if (chunks.size() < 2) return chunks;

        List<Chunk> result = new ArrayList<>(chunks.size());
        result.add(chunks.get(0));
        for (int i = 1; i < chunks.size(); i++) {
            Chunk prev = chunks.get(i - 1);
            Chunk cur = chunks.get(i);

            if (looksLikeTableChunk(cur.text()) || looksLikeTableChunk(prev.text())) {
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

    /**
     * 줄 단위로 단락형 표(한 줄에 모든 행이 들어간 표) 를 식별하고 변환한다.
     * - 변환 1: 순차 NUMBER(1, 2, 3...) 매치 위치마다 줄바꿈 삽입 → 기존 줄 단위 표 휴리스틱 활성화
     * - 변환 2: 표 항목 이름 추출 후 청크 시작에 "표 항목: A, B, C." 자연어 prefix 첨가 →
     *           표 청크가 자연어 query 와 embedding 거리상 가까워짐 (retrieval 정확도 향상)
     *
     * 이미 줄 단위로 분리된 표(`1 ...\n2 ...\n3 ...`)는 한 줄에 NUMBER 1개씩만 있어 자연스럽게
     * skip 되고, 한 줄에 다 들어간 단락형 표만 expand 된다.
     */
    private String expandParagraphTables(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) result.append('\n');
            result.append(expandTableInLine(lines[i]));
        }
        return result.toString();
    }

    private String expandTableInLine(String paragraph) {
        List<int[]> tableRows = findSequentialNumberPositions(paragraph);
        if (tableRows.size() < MIN_TABLE_ROWS) return paragraph;

        int firstMatch = tableRows.get(0)[0];
        String beforeTable = paragraph.substring(0, firstMatch).replaceAll("\\s+$", "");
        String tablePart = paragraph.substring(firstMatch);

        // NUMBER 마다 줄바꿈 삽입 (역순으로 처리해 인덱스 안정)
        StringBuilder sb = new StringBuilder(tablePart);
        for (int i = tableRows.size() - 1; i > 0; i--) {
            int pos = tableRows.get(i)[0] - firstMatch;
            if (pos > 0 && pos < sb.length()) {
                char prev = sb.charAt(pos - 1);
                if (Character.isWhitespace(prev)) {
                    sb.setCharAt(pos - 1, '\n');
                } else {
                    sb.insert(pos, '\n');
                }
            }
        }
        String linearizedTable = sb.toString();

        // 표 항목 이름 추출 (각 행의 NUMBER 뒤 첫 토큰)
        List<String> names = extractRowNames(linearizedTable);
        String prefix = names.isEmpty()
                ? "[표]"
                : "표 항목: " + String.join(", ", names) + ".";

        StringBuilder out = new StringBuilder();
        if (!beforeTable.isEmpty()) {
            out.append(beforeTable).append('\n');
        }
        out.append(prefix).append('\n');
        out.append(linearizedTable);
        return out.toString();
    }

    /**
     * 단락 안에서 순차 NUMBER(1, 2, 3, ...) 가 등장하는 위치를 추출.
     * 한 줄/단락에 여러 표가 있을 수 있으므로(예: 페이지 36 의 "중복 가능 1~11" + "그 외 30~41")
     * 길이 >= MIN_TABLE_ROWS 인 모든 chain 의 위치를 합쳐 반환한다. 위치는 paragraph 기준 오름차순.
     */
    private List<int[]> findSequentialNumberPositions(String paragraph) {
        Pattern numPat = Pattern.compile("\\d+");
        Matcher m = numPat.matcher(paragraph);
        List<int[]> all = new ArrayList<>();
        while (m.find()) {
            try {
                int n = Integer.parseInt(m.group());
                if (n > 0 && n < 1000) {
                    all.add(new int[]{m.start(), m.end(), n});
                }
            } catch (NumberFormatException ignored) {
            }
        }

        boolean[] used = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) {
            if (used[i]) continue;
            List<Integer> chainIndices = new ArrayList<>();
            chainIndices.add(i);
            int currentNum = all.get(i)[2];
            for (int j = i + 1; j < all.size(); j++) {
                if (used[j]) continue;
                if (all.get(j)[2] == currentNum + 1) {
                    chainIndices.add(j);
                    currentNum++;
                }
            }
            if (chainIndices.size() >= MIN_TABLE_ROWS) {
                for (int idx : chainIndices) used[idx] = true;
            }
        }

        List<int[]> result = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (used[i]) result.add(all.get(i));
        }
        return result;
    }

    private static final Pattern ROW_NAME = Pattern.compile("(?:^|\\n)\\d+\\s+(\\S+)");

    private List<String> extractRowNames(String linearizedTable) {
        Matcher m = ROW_NAME.matcher(linearizedTable);
        List<String> names = new ArrayList<>();
        while (m.find() && names.size() < 50) {
            names.add(m.group(1));
        }
        return names;
    }

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
                    // block end 는 마지막 표 행의 trailing newline 까지 포함 (off-by-one 보정).
                    // cursor 는 현재 비-표 줄의 시작 위치이므로, 직전 \n 의 다음 위치 = cursor.
                    blocks.add(new TableBlock(blockStart, cursor, header));
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
        // 방어: 직전 줄 자체가 number row 패턴이면 헤더로 잡지 않는다.
        // (정상 흐름에서는 prevLine 은 비-표 줄이지만, 표 인식 규칙이 미래에 바뀌어
        // number row 가 prev 로 들어올 가능성에 대비.)
        if (NUMBER_ROW.matcher(candidate).find()) {
            return null;
        }
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
