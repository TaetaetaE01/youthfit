package com.youthfit.guide.infrastructure.external;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.common.util.TokenCounter;
import com.youthfit.guide.application.dto.command.GuideGenerationInput;
import com.youthfit.guide.application.port.GuideLlmProvider;
import com.youthfit.guide.domain.model.GuideContent;
import com.youthfit.guide.domain.model.GuideGroup;
import com.youthfit.guide.domain.model.GuideHighlight;
import com.youthfit.guide.domain.model.GuideListSection;
import com.youthfit.guide.domain.model.GuidePairedSection;
import com.youthfit.guide.domain.model.GuidePitfall;
import com.youthfit.guide.domain.model.GuideSourceField;
import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiChatClient implements GuideLlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatClient.class);
    private static final String CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";

    static final String PARTIAL_SYSTEM_PROMPT_PREFIX = """
            [부분 가이드 호출 안내]
            이 호출은 정책 전체가 아니라 청크 그룹 하나만으로 작성하는 부분 가이드다.
            다른 부분 가이드들과 추후 통합 호출에서 병합된다.
            보이는 청크 내용 + 정책 메타에 한해서만 응답하라.
            누락된 정보를 추측하지 마라. 다른 청크 그룹에 있을 법한 내용을 가정하지 마라.

            """;

    static final String MERGE_SYSTEM_PROMPT = """
            너는 한국 청년 정책 가이드 통합 편집자다. 입력으로 정책 메타와 여러 부분 가이드(JSON) 들이 주어진다.
            목표는 부분들을 합쳐 중복을 제거하고 우선순위가 높은 항목을 보존한 최종 가이드를 작성하는 것이다.

            [통합 원칙]
            1. 정보 통제: 부분 가이드와 정책 메타에 명시된 내용만 사용한다. 추가 정보를 만들어내지 마라.
            2. 중복 제거: 의미가 동일한 highlight/pitfall 은 하나로 합치되 더 구체적인 표현을 우선한다.
            3. 출처 보존: sourceField 와 attachmentRef 는 가능한 보존한다.
            4. 어조/품질: 기존 가이드 작성 원칙(단정형 어미, 친근체 금지, 수치 보존) 을 동일하게 적용한다.
            5. 분류 키워드 분리: 차상위/일반공급/특별공급 등 분류가 다른 항목은 다른 group 으로 유지한다.

            출력 schema 는 단일 호출 가이드와 동일하다.
            """;

    static final String SYSTEM_PROMPT = """
            너는 한국 청년 정책 가이드 작성 전문가다. 대상 독자는 정책 용어를 처음 접하는 20대 일반 청년이다.
            가장 중요한 임무는 복잡한 행정·법률 용어를 독자가 즉시 자신의 상황에 대입할 수 있는 직관적인 일상어로 번역하는 것이다.
            원문을 그대로 복사하여 붙여넣는 것은 실패로 간주한다.

            [작성 원칙]
            1. 정보 통제: 입력된 원문에만 근거하여 작성한다. 원문에 없는 조건, 금액, 기한, 자격을 절대 추가하지 않는다.
            2. 수치 보존: 원문에 명시된 모든 금액, 연령, 기간, 지역, 비율 수치는 누락 없이 보존한다.
            3. 용어 순화 (필수 매핑):
               - "무주택세대구성원" → "주민등록등본상 함께 올라가 있는 가족 전체가 집을 소유하지 않은 사람"
               - "차상위계층" → "기초생활수급자보다는 소득이 조금 높지만 일반 가구보다는 낮은 계층 (정부가 정한 기준 이하)"
               - "전년도 도시근로자 가구당 월평균소득 100% 이하" → "작년 도시 직장인 가구의 한 달 평균 소득 이하"
               - "임대의무기간" → "법으로 정해진 거주 의무 기간"
               - "입주자모집공고일" → "정부가 입주자를 모집한다고 공식 발표한 날"
            4. 문장 구조: 한 문장 당 한 가지의 조건만 담아, 짧고 능동적인 불릿 포인트 형태로 분할한다.
            5. 독립성 유지(중복 분리, 강화):
               - 분류 키워드(차상위 초과/이하, 일반공급/특별공급, 미혼/기혼·맞벌이, 1인/다인 가구 등)가 다르면 절대 한 group에 섞지 않는다.
               - 같은 group 안에 서로 다른 분류 키워드가 등장하면 group 분할 실패로 간주.
               - 각 group의 label에 분류명을 명시한다 (예: "차상위계층 이하 (소득 기준)").
            6. 어조: 객관적이고 명확한 단정형 어미("~다", "~함", 명사형 종결)를 사용한다. 친근한 어투("~해요", "~예요", "~드려요")는 절대 금지.
            7. 추정 금지: "~일 수 있다", "~로 추정" 등 가정 금지. 입력 [원문] 또는 [참고 - 환산표]에 명시되지 않은 환경값을 임의로 만들지 않는다.
            8. 완전한 재구성: 원문이 이미 쉬워 보이더라도 독자 관점에서 행동 지향적인 문장으로 100% 다시 쓴다.
            9. 환산값 표기 규칙:
               - "중위소득 N% 이하", "차상위계층" 같은 비율·분류 표기는 풀이에 환산 금액을 병기한다.
               - 우선순위:
                 (a) [원문 - 첨부 청크]에 환산 금액이 명시되어 있으면 그 값을 그대로 인용. 가구원 수 범위가 명시되어 있으면 그 범위를 따른다.
                 (b) (a)가 없으면 [참고 - 환산표]의 1·2인 가구 기준 금액을 사용한다.
               - 표기 형식: "중위소득 60% 이하 (2025년 기준 1인 가구 월 약 143만원, 2인 가구 월 약 233만원)"
               - 차상위계층 표기: "차상위계층 이하 (2025년 기준 1인 가구 월 약 119만원 이하)"
               - [참고 - 환산표]에도 없으면 비율만 표기 (만들어내지 않는다).
               - 같은 풀이 안에서 동일 비율이 반복 등장하면 환산값은 첫 등장에만 병기 (가독성).
            10. 출처 라벨 정확성 (첨부 trace):
                - highlights / pitfalls 의 sourceField 는 정보가 발견된 청크 라벨의 source 값을 그대로 쓴다.
                - source=ATTACHMENT 인 청크에서 가져온 정보:
                  · attachmentRef.attachmentId = 청크 라벨의 attachment-id 그대로
                  · attachmentRef.pageStart / pageEnd = 청크 라벨의 pages= 범위 그대로
                  · 청크 라벨에 pages 가 없으면 pageStart / pageEnd = null (HWP 등)
                  · 라벨에 없는 페이지를 추측해서 박지 말 것
                - source=ENRICHMENT_BODY 인 청크에서 가져온 정보는 sourceField=ENRICHMENT 로 라벨링한다.
                  이 청크는 외부 정책 안내 페이지 본문 발췌이며 attachmentId 는 없다. attachmentRef 도 비워둔다.
                - sourceField != ATTACHMENT 일 때 attachmentRef = null
                - 여러 청크에 걸친 정보면 가장 핵심 정보가 있는 청크 1개를 선택해 그 라벨 메타를 박는다.
                - **[강제 규칙]** [정책 메타]의 attachmentIds 가 비어있지 않으면 highlights/pitfalls 합쳐서
                  **최소 2개 항목은 sourceField=ATTACHMENT** 로 박아야 한다 (첨부 PDF에서 추출한 디테일이
                  사용자에게 노출되어야 trace 가 의미를 가짐). 첨부 청크 (source=ATTACHMENT) 텍스트에
                  자격 조건·중복 수혜·예외·세부 절차·서류 요건 등이 풍부하게 들어있으니 적극 활용한다.
                  attachmentIds 가 여러 개면 각 첨부에서 최소 1개씩 인용을 권장한다.

            [변환 예시 6] 첨부 trace:
            입력 청크: `[chunk-1 source=ATTACHMENT attachment-id=12 pages=35-35]\n배우자 명의 자가 주택이 있는 경우도 본 사업의 중복 수혜 제한 대상에 포함된다.`
            → pitfalls 출력:
            { "text": "배우자 명의 자가 주택이 있어도 신청 제외", "sourceField": "ATTACHMENT",
              "attachmentRef": { "attachmentId": 12, "pageStart": 35, "pageEnd": 35 } }

            [변환 예시 7] enrichment 흡수 (온통청년 정책, 본문 빈약):
            입력:
              [supportTarget]
              (빈 값 또는 짧은 요약)

              [enrichment.supportTarget]
              서울특별시 거주 만 19~34세 청년 무주택자

              [enrichment.applyMethod]
              1) 온통청년 홈페이지 회원가입
              2) 신청서 작성 후 첨부서류 업로드
              3) 자치구 청년정책팀 심사 후 결과 통보

              [enrichment.requiredDocuments]
              주민등록등본, 최근 3개월 건강보험 자격득실확인서, 임대차계약서 사본

              [enrichment.deadlineNote]
              2026-03-01 ~ 2026-05-31

              [enrichment.operatingOrganization]
              서울특별시 청년정책담당관

              [enrichment.contactPhone]
              02-2133-6586

            → 출력 일부:
              "target": {
                "groups": [
                  { "label": null,
                    "items": [
                      "서울특별시 거주 중인 만 19~34세 청년",
                      "본인 명의의 집을 소유하지 않은 무주택자"
                    ] }
                ]
              },
              "applyMethod": {
                "items": [
                  "온통청년 홈페이지에서 회원가입한다",
                  "신청서를 작성하고 첨부서류를 업로드한다",
                  "자치구 청년정책팀 심사 후 결과 통보를 받는다"
                ],
                "sourceField": "ENRICHMENT",
                "attachmentRef": null
              },
              "deadlineNote": {
                "items": ["2026-03-01 ~ 2026-05-31"],
                "sourceField": "ENRICHMENT",
                "attachmentRef": null
              },
              "requiredDocuments": {
                "items": ["주민등록등본", "최근 3개월 건강보험 자격득실확인서", "임대차계약서 사본"],
                "sourceField": "ENRICHMENT",
                "attachmentRef": null
              },
              "contact": {
                "items": ["서울특별시 청년정책담당관", "02-2133-6586"],
                "sourceField": "ENRICHMENT",
                "attachmentRef": null
              }

            [변환 예시 8] 본문 우선, enrichment 무시:
            입력:
              [contact]
              서울특별시 청년정책담당관 02-2133-6586 youth@seoul.go.kr

              [enrichment.operatingOrganization]
              서울특별시 청년정책담당관

              [enrichment.contactPhone]
              02-2133-6586

            → 출력:
              "contact": {
                "items": ["서울특별시 청년정책담당관 02-2133-6586", "youth@seoul.go.kr"],
                "sourceField": "SUPPORT_CONTENT",
                "attachmentRef": null
              }
            (본문에 동일 정보가 있으면 enrichment 가 아닌 본문 라벨 사용)

            [출력 단위 — JSON]
            - oneLineSummary: 정책 정체를 누가/무엇을/얼마나 받는지 1~2문장.
            - highlights: 사용자가 PDF를 보지 않고도 정책의 핵심 특징을 파악할 수 있는 항목 3~6개.
              혜택의 강도, 차별점, 신청 시점/방법의 특이사항, 우대조건, 중복 수혜 가능 여부 등 긍정·중립 정보.
              각 항목 sourceField 라벨 필수 (SUPPORT_TARGET / SELECTION_CRITERIA / SUPPORT_CONTENT / BODY / ATTACHMENT / ENRICHMENT).
            - target / criteria / content: 각각 supportTarget / selectionCriteria / supportContent 풀이 (groups 배열).
              입력이 비어있으면 null. groups 구조는 아래 [변환 예시] 참조.
            - applyMethod / deadlineNote / requiredDocuments / contact: 복지로식 정형 섹션.
              각각 신청방법 / 신청기한 / 제출서류 / 문의처. 각 항목은 { "items": [...], "sourceField": "...", "attachmentRef": null|... } 형태.
              본문과 enrichment 양쪽에 정보가 없으면 null. items 가 비어있는 형태로 만들지 말 것.
              contact 는 operatingOrganization + contactPhone 을 같은 items 리스트에 줄 단위로 배치.
              deadlineNote.items 는 가능하면 "YYYY-MM-DD ~ YYYY-MM-DD" 또는 사람이 읽는 형태("상시", "예산 소진 시까지").
            - pitfalls: 부정·함정·예외·제외 조건만 (자격 미달 트리거, 중복 수혜 제한, 사후 의무, 신청기한 외).
              긍정·중립 정보는 highlights로 보낸다. 각 항목 sourceField 라벨 필수.

            [ENRICHMENT 라벨 사용 규칙]
            - [enrichment.*] 절은 외부 정책 안내 페이지에서 AI 가 자동 추출한 보조 정보다.
            - 본문 절([body], [supportTarget], [selectionCriteria], [supportContent]) 에 같거나 유사한 정보가 있으면
              본문의 sourceField (BODY / SUPPORT_TARGET / SELECTION_CRITERIA / SUPPORT_CONTENT) 를 **우선** 사용한다.
            - 본문에 없고 [enrichment.*] 절에만 있는 정보만 sourceField=ENRICHMENT 로 라벨링한다.
            - [enrichment.eligibilityCriteria] 는 본문의 [selectionCriteria] 와 동의어로 취급. criteria 섹션 입력으로 활용.
            - [enrichment.policyOverview] 는 별도 출력 섹션을 만들지 말고, oneLineSummary / highlights 작성 시 보조 입력으로만 활용.
            - [enrichment.meta] 는 신뢰도/출처 참고 정보다. 본문 인용에 직접 사용하지 않는다.

            [중요] 첨부(PDF/HWP) 본문에 다음 정보가 명시되어 있으면 반드시 highlights 또는 pitfalls 에 1개 이상 포함한다 (정책 본문에 없어도 첨부에서 끌어올 것):
            - 다른 유사 정책과의 중복 수혜 제한 / 동시 수급 불가 리스트 (예: "청년희망키움통장과 중복 신청 불가")
            - 가입·수급 후 사후 의무 (예: "3년 만기 전 중도 해지 시 적립금 일부 환수")
            - 자격 박탈·환수 트리거 (예: "근로활동 미유지 시 지원금 환수")
            - 우선 공급 비율 / 가산점 / 우대 조건 (예: "70%는 월평균소득 100% 이하에 우선 공급")
            이런 항목들은 사용자가 PDF를 직접 안 봐도 가이드만으로 의사결정할 수 있도록 만드는 핵심 가치다. 누락 금지.

            각 paired section의 groups 구조:
            - 분류가 명확히 구분되는 경우: 각 분류를 별도 group 객체로 만들어 label 필드에 분류명을 명시.
            - 분류가 없는 단순 나열: 단일 group 객체에 label은 null.
            - 한 group은 label(문자열 또는 null)과 items(불릿 문자열 배열)를 가진다. items는 절대 비울 수 없다.

            [변환 예시 1] supportTarget 원문:
            "입주자모집공고일 현재 해당 주택건설지역에 거주하는 무주택세대구성원으로서 해당 세대의 소득 및 보유자산이 국토교통부장관이 정하는 기준 이하인 자"
            → target output:
            {
              "groups": [
                { "label": null,
                  "items": [
                    "정부가 입주자를 모집한다고 공식 발표한 날 기준으로 해당 주택이 지어지는 지역에 거주 중일 것",
                    "주민등록등본상 함께 올라가 있는 가족 전체가 집을 소유하지 않은 사람일 것",
                    "가구의 소득과 자산(토지·건물·자동차)이 국토교통부가 정한 기준 이하일 것"
                  ] }
              ]
            }

            [변환 예시 2] selectionCriteria 원문 (분류가 명확):
            "(일반공급) 전용면적 60㎡ 이하 공공분양 : 전년도 도시근로자 가구당 월평균소득 100% 이하 / (생애최초 특별공급) 전년도 도시근로자가구 평균소득 130% 이하"
            → criteria output:
            {
              "groups": [
                { "label": "일반공급 - 소득 기준",
                  "items": ["전용면적 60㎡ 이하 공공분양 주택의 경우 작년 도시 직장인 가구의 한 달 평균 소득 이하일 때 신청 가능"] },
                { "label": "생애최초 특별공급 - 소득 기준",
                  "items": ["작년 도시 직장인 가구의 한 달 평균 소득의 130% 이하일 때 신청 가능"] }
              ]
            }

            [변환 예시 3] 차상위 분류 분리 (정책 7번류):
            selectionCriteria 원문: "차상위계층 이하: 월 30만원 지원 / 차상위 초과 ~ 중위소득 60% 이하: 월 20만원 지원"
            → criteria output:
            {
              "groups": [
                { "label": "차상위계층 이하",
                  "items": [
                    "기초생활수급자보다는 소득이 조금 높지만 일반 가구보다는 낮은 계층 (2025년 기준 1인 가구 월 약 119만원 이하)에 해당",
                    "월 30만원 지원"
                  ] },
                { "label": "차상위 초과 ~ 중위소득 60% 이하",
                  "items": [
                    "차상위계층보다는 소득이 높지만 중위소득 60% 이하 (2025년 기준 1인 가구 월 약 143만원, 2인 가구 월 약 233만원) 까지 해당",
                    "월 20만원 지원"
                  ] }
              ]
            }

            [변환 예시 4] 환산값 PDF 우선:
            chunk 안에 "2025년 기준 1·2인 가구 월 138/230만원 이하" 가 명시되어 있다면, 풀이는 그 값을 그대로 인용한다.
            [참고 - 환산표]의 143만원 / 233만원 보다 우선.
            → 풀이 텍스트: "중위소득 60% 이하 (2025년 1인 가구 월 138만원, 2인 230만원 — 정책 본문 기준)"

            [변환 예시 5] highlights vs pitfalls 분리:
            같은 정책 입력에 대해
            - highlights (긍정·중립·차별점):
              { "text": "월 최대 20만원 월세 지원", "sourceField": "SUPPORT_CONTENT" }
              { "text": "다른 청년 주거 지원과 중복 수혜 가능", "sourceField": "SUPPORT_CONTENT" }
              { "text": "신청 후 평균 2주 내 지급 결정", "sourceField": "SUPPORT_CONTENT" }
            - pitfalls (부정·함정·예외):
              { "text": "월세 60만원 초과 주택은 대상 제외", "sourceField": "SUPPORT_TARGET" }
              { "text": "최초 신청일로부터 3개월 내 입주 확인 필요", "sourceField": "SUPPORT_CONTENT" }
            """;

    private final OpenAiChatProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final TokenCounter tokenCounter;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${guide.map-reduce.merge-cap-tokens:100000}")
    private int mergeCapTokens;

    @Override
    public GuideContent generateGuide(GuideGenerationInput input) {
        return callChatCompletion(SYSTEM_PROMPT, buildUserMessage(input), input.policyId());
    }

    @Override
    public GuideContent regenerateWithFeedback(GuideGenerationInput input, List<String> feedbackMessages) {
        return callChatCompletion(SYSTEM_PROMPT, buildUserMessageWithFeedback(input, feedbackMessages), input.policyId());
    }

    @Override
    public GuideContent generatePartialGuide(GuideGenerationInput input) {
        return callChatCompletion(
                PARTIAL_SYSTEM_PROMPT_PREFIX + SYSTEM_PROMPT,
                buildUserMessage(input),
                input.policyId()
        );
    }

    @Override
    public GuideContent mergePartialGuides(GuideGenerationInput input, List<GuideContent> partials) {
        String userMessage = buildMergeUserMessage(input, partials);
        int total = tokenCounter.countTokens(MERGE_SYSTEM_PROMPT + userMessage, properties.getModel());
        if (total > mergeCapTokens) {
            log.error("merge 호출 prompt 한도 초과: policyId={}, tokens={}, cap={}, partials={}",
                    input.policyId(), total, mergeCapTokens, partials.size());
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR,
                    "merge 호출 prompt 가 token cap 을 초과: policyId=" + input.policyId() + ", tokens=" + total);
        }
        return callChatCompletion(MERGE_SYSTEM_PROMPT, userMessage, input.policyId());
    }

    private GuideContent callChatCompletion(String systemPrompt, String userMessage, Long policyId) {
        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)
                ),
                "response_format", buildResponseFormat()
        );

        JsonNode response = restClient.post()
                .uri(CHAT_COMPLETIONS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
            log.error("OpenAI Chat API 호출 실패: policyId={}", policyId);
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "가이드 생성에 실패했습니다");
        }

        emitMetric(response);

        String json = response.get("choices").get(0).get("message").get("content").asText();
        log.info("가이드 생성 완료: policyId={}, 응답 길이={}", policyId, json.length());
        return parseGuideContent(json);
    }

    private String buildMergeUserMessage(GuideGenerationInput input, List<GuideContent> partials) {
        StringBuilder sb = new StringBuilder();
        sb.append("[정책 메타]\n");
        sb.append("title=").append(input.title()).append("\n");
        if (input.summary() != null) sb.append("summary=").append(input.summary()).append("\n");
        if (input.supportTarget() != null) sb.append("supportTarget=").append(input.supportTarget()).append("\n");
        if (input.supportContent() != null) sb.append("supportContent=").append(input.supportContent()).append("\n");
        if (input.organization() != null) sb.append("organization=").append(input.organization()).append("\n");
        if (input.contact() != null) sb.append("contact=").append(input.contact()).append("\n");
        sb.append("\n");

        for (int i = 0; i < partials.size(); i++) {
            sb.append("[부분 가이드 ").append(i + 1).append(" / ").append(partials.size()).append("]\n");
            try {
                sb.append(objectMapper.writeValueAsString(partials.get(i))).append("\n\n");
            } catch (Exception e) {
                log.error("부분 가이드 직렬화 실패: policyId={}, partialIndex={}",
                        input.policyId(), i, e);
                throw new YouthFitException(ErrorCode.INTERNAL_ERROR,
                        "부분 가이드 직렬화 실패: policyId=" + input.policyId() + ", partialIndex=" + i);
            }
        }
        return sb.toString();
    }

    private String buildUserMessageWithFeedback(GuideGenerationInput input, List<String> feedback) {
        String base = buildUserMessage(input);
        StringBuilder sb = new StringBuilder(base);
        sb.append("\n[이전 응답 검증 실패 — 다음을 고쳐서 다시 작성할 것]\n");
        feedback.forEach(f -> sb.append("- ").append(f).append("\n"));
        return sb.toString();
    }

    GuideContent parseGuideContent(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String oneLine = node.get("oneLineSummary").asText();
            List<GuideHighlight> highlights = parseHighlights(node.get("highlights"));
            GuidePairedSection target = parsePaired(node.get("target"));
            GuidePairedSection criteria = parsePaired(node.get("criteria"));
            GuidePairedSection content = parsePaired(node.get("content"));
            GuideListSection applyMethod = parseListSection(node.get("applyMethod"));
            GuideListSection deadlineNote = parseListSection(node.get("deadlineNote"));
            GuideListSection requiredDocuments = parseListSection(node.get("requiredDocuments"));
            GuideListSection contact = parseListSection(node.get("contact"));
            List<GuidePitfall> pitfalls = parsePitfalls(node.get("pitfalls"));
            return new GuideContent(
                    oneLine, highlights, target, criteria, content,
                    applyMethod, deadlineNote, requiredDocuments, contact,
                    pitfalls
            );
        } catch (Exception e) {
            log.error("가이드 응답 JSON 파싱 실패: {}", json, e);
            throw new YouthFitException(ErrorCode.INTERNAL_ERROR, "가이드 응답 파싱 실패");
        }
    }

    private GuideListSection parseListSection(JsonNode node) {
        if (node == null || node.isNull()) return null;
        JsonNode itemsNode = node.get("items");
        if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty()) return null;
        List<String> items = new ArrayList<>();
        itemsNode.forEach(n -> items.add(n.asText()));

        JsonNode sourceFieldNode = node.get("sourceField");
        if (sourceFieldNode == null || sourceFieldNode.isNull()) return null;
        GuideSourceField sourceField;
        try {
            sourceField = GuideSourceField.valueOf(sourceFieldNode.asText());
        } catch (IllegalArgumentException e) {
            log.warn("invalid sourceField in list section: {}", sourceFieldNode.asText());
            return null;
        }

        com.youthfit.guide.domain.model.AttachmentRef ref = parseAttachmentRef(node.get("attachmentRef"));
        try {
            return new GuideListSection(items, sourceField, ref);
        } catch (IllegalArgumentException e) {
            log.warn("invalid GuideListSection from LLM: items={} sourceField={} ref={}, msg={}",
                    items, sourceField, ref, e.getMessage());
            return null;
        }
    }

    private List<GuideHighlight> parseHighlights(JsonNode node) {
        List<GuideHighlight> highlights = new ArrayList<>();
        if (node == null || !node.isArray()) return highlights;
        node.forEach(n -> {
            String text = n.get("text").asText();
            String sourceFieldStr = n.get("sourceField").asText();
            highlights.add(new GuideHighlight(
                    text,
                    GuideSourceField.valueOf(sourceFieldStr),
                    parseAttachmentRef(n.get("attachmentRef"))));
        });
        return highlights;
    }

    private com.youthfit.guide.domain.model.AttachmentRef parseAttachmentRef(JsonNode node) {
        if (node == null || node.isNull()) return null;
        JsonNode idNode = node.get("attachmentId");
        if (idNode == null || idNode.isNull()) return null;

        Long attachmentId = idNode.asLong();
        JsonNode startNode = node.get("pageStart");
        JsonNode endNode = node.get("pageEnd");
        Integer pageStart = (startNode == null || startNode.isNull()) ? null : startNode.asInt();
        Integer pageEnd = (endNode == null || endNode.isNull()) ? null : endNode.asInt();

        try {
            return new com.youthfit.guide.domain.model.AttachmentRef(attachmentId, pageStart, pageEnd);
        } catch (IllegalArgumentException e) {
            // LLM 이 잘못된 page range 박은 경우 (pageStart > pageEnd 또는 한쪽만 존재) → null fallback,
            // 후속 GuideValidator 검증 5 가 retry 트리거
            log.warn("invalid attachmentRef from LLM: id={} start={} end={}, message={}",
                    attachmentId, pageStart, pageEnd, e.getMessage());
            return null;
        }
    }

    private GuidePairedSection parsePaired(JsonNode node) {
        if (node == null || node.isNull()) return null;
        JsonNode groupsNode = node.get("groups");
        if (groupsNode == null || !groupsNode.isArray() || groupsNode.isEmpty()) return null;

        List<GuideGroup> groups = new ArrayList<>();
        groupsNode.forEach(g -> {
            JsonNode labelNode = g.get("label");
            String label = (labelNode == null || labelNode.isNull()) ? null : labelNode.asText();
            JsonNode itemsNode = g.get("items");
            if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty()) return;
            List<String> items = new ArrayList<>();
            itemsNode.forEach(n -> items.add(n.asText()));
            groups.add(new GuideGroup(label, items));
        });

        if (groups.isEmpty()) return null;
        return new GuidePairedSection(groups);
    }

    private List<GuidePitfall> parsePitfalls(JsonNode node) {
        List<GuidePitfall> pitfalls = new ArrayList<>();
        if (node == null || !node.isArray()) return pitfalls;
        node.forEach(n -> {
            String text = n.get("text").asText();
            String sourceFieldStr = n.get("sourceField").asText();
            pitfalls.add(new GuidePitfall(
                    text,
                    GuideSourceField.valueOf(sourceFieldStr),
                    parseAttachmentRef(n.get("attachmentRef"))));
        });
        return pitfalls;
    }

    private String buildUserMessage(GuideGenerationInput input) {
        StringBuilder sb = new StringBuilder();
        sb.append("[정책 메타]\n");
        sb.append("title: ").append(input.title()).append("\n");
        if (input.referenceYear() != null) sb.append("referenceYear: ").append(input.referenceYear()).append("\n");
        if (input.organization() != null) sb.append("organization: ").append(input.organization()).append("\n");
        if (input.contact() != null) sb.append("contact: ").append(input.contact()).append("\n");

        // 이 정책의 첨부 ID 화이트리스트 — LLM 이 attachmentRef.attachmentId 를 이 목록에서만 고르도록 강제
        java.util.LinkedHashSet<Long> attachmentIds = new java.util.LinkedHashSet<>();
        for (var c : input.chunks()) {
            if (c.attachmentId() != null) attachmentIds.add(c.attachmentId());
        }
        if (!attachmentIds.isEmpty()) {
            sb.append("attachmentIds: ").append(attachmentIds).append("\n");
            sb.append("(sourceField=ATTACHMENT 일 때 attachmentRef.attachmentId 는 위 목록에서만 사용. ")
              .append("few-shot 예시의 attachmentId=12 는 단순 형식 예시이며 실제 값으로 사용 금지.)\n");
        }

        sb.append("\n[원문]\n");
        sb.append(input.combinedSourceText());
        if (input.referenceData() != null) {
            sb.append("\n").append(input.referenceData().toContextText());
        }
        return sb.toString();
    }

    private void emitMetric(JsonNode response) {
        try {
            JsonNode usage = response == null ? null : response.get("usage");
            int prompt = usage == null || !usage.has("prompt_tokens") ? 0 : usage.get("prompt_tokens").asInt();
            int completion = usage == null || !usage.has("completion_tokens") ? 0 : usage.get("completion_tokens").asInt();
            String model = properties.getModel();
            eventPublisher.publishEvent(new LlmCallRecorded(
                    LlmModule.GUIDE, model, prompt, completion, Instant.now()
            ));
        } catch (Exception e) {
            log.warn("guide LLM 비용 이벤트 발행 실패 (정상 흐름 진행)", e);
        }
    }

    private Map<String, Object> buildResponseFormat() {
        Map<String, Object> groupSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("label", "items"),
                "properties", Map.of(
                        "label", Map.of("anyOf", List.of(
                                Map.of("type", "string"),
                                Map.of("type", "null")
                        )),
                        "items", Map.of("type", "array", "items", Map.of("type", "string"))
                )
        );

        Map<String, Object> pairedSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("groups"),
                "properties", Map.of(
                        "groups", Map.of("type", "array", "items", groupSchema)
                )
        );

        Map<String, Object> attachmentRefSchema = Map.of(
                "anyOf", List.of(
                        Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "required", List.of("attachmentId", "pageStart", "pageEnd"),
                                "properties", Map.of(
                                        "attachmentId", Map.of("type", "integer"),
                                        "pageStart", Map.of("anyOf", List.of(
                                                Map.of("type", "integer"),
                                                Map.of("type", "null")
                                        )),
                                        "pageEnd", Map.of("anyOf", List.of(
                                                Map.of("type", "integer"),
                                                Map.of("type", "null")
                                        ))
                                )
                        ),
                        Map.of("type", "null")
                )
        );

        List<String> sourceFieldEnum = List.of(
                "SUPPORT_TARGET", "SELECTION_CRITERIA", "SUPPORT_CONTENT",
                "BODY", "ATTACHMENT", "ENRICHMENT"
        );

        Map<String, Object> pitfallSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("text", "sourceField", "attachmentRef"),
                "properties", Map.of(
                        "text", Map.of("type", "string"),
                        "sourceField", Map.of("type", "string", "enum", sourceFieldEnum),
                        "attachmentRef", attachmentRefSchema
                )
        );

        Map<String, Object> listSectionSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("items", "sourceField", "attachmentRef"),
                "properties", Map.of(
                        "items", Map.of("type", "array", "items", Map.of("type", "string"), "minItems", 1),
                        "sourceField", Map.of("type", "string", "enum", sourceFieldEnum),
                        "attachmentRef", attachmentRefSchema
                )
        );

        Map<String, Object> nullableListSection = Map.of(
                "anyOf", List.of(listSectionSchema, Map.of("type", "null"))
        );

        java.util.LinkedHashMap<String, Object> properties = new java.util.LinkedHashMap<>();
        properties.put("oneLineSummary", Map.of("type", "string"));
        properties.put("highlights", Map.of("type", "array", "items", pitfallSchema));
        properties.put("target", Map.of("anyOf", List.of(pairedSchema, Map.of("type", "null"))));
        properties.put("criteria", Map.of("anyOf", List.of(pairedSchema, Map.of("type", "null"))));
        properties.put("content", Map.of("anyOf", List.of(pairedSchema, Map.of("type", "null"))));
        properties.put("applyMethod", nullableListSection);
        properties.put("deadlineNote", nullableListSection);
        properties.put("requiredDocuments", nullableListSection);
        properties.put("contact", nullableListSection);
        properties.put("pitfalls", Map.of("type", "array", "items", pitfallSchema));

        java.util.LinkedHashMap<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of(
                "oneLineSummary", "highlights", "target", "criteria", "content",
                "applyMethod", "deadlineNote", "requiredDocuments", "contact", "pitfalls"
        ));
        schema.put("properties", properties);

        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "guide_content",
                        "strict", true,
                        "schema", schema
                )
        );
    }
}
