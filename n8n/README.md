# n8n 크롤링 워크플로우

## 개요
청년몽땅정보통(youth.seoul.go.kr)에서 청년 정책 데이터를 수집하여 YouthFit 백엔드로 전달하는 n8n 워크플로우.

## 워크플로우 흐름

```
[Schedule Trigger] 매일 새벽 03:00
       |
[HTTP Request] 목록 페이지 (pageIndex=1~)
       |
[Code Node] goView('V202600006') 패턴에서 plcyBizId 추출
       |
[Split In Batches] 정책별 순차 처리
       |
[Wait 3초] Rate Limit
       |
[HTTP Request] 상세 페이지 (view.do?plcyBizId=...)
       |
[Code Node] HTML 파싱 (제목, 본문, 카테고리, 신청기간)
       |
[HTTP Request] POST /api/internal/ingestion/policies
       |
[IF] 다음 페이지 존재 -> 목록 요청 반복
```

## 사전 준비

1. Docker Compose로 전체 서비스 실행:
   ```bash
   docker compose up -d
   ```

2. n8n UI 접속: http://localhost:5678
   - ID: `admin` / PW: `changeme` (로컬 개발용)

## 워크플로우 Import

1. n8n UI 접속 후 좌측 메뉴에서 **Workflows** 클릭
2. 우측 상단 **...** 메뉴 -> **Import from File** 선택
3. `n8n/workflows/youth-seoul-crawl.json` 파일 선택
4. Import 완료 후 워크플로우 열기

## 환경변수 설정

docker-compose.yml에 이미 설정되어 있음:

| 변수 | 값 | 설명 |
|------|-----|------|
| `INTERNAL_API_KEY` | `.env`에서 주입 | 백엔드 내부 API 인증 키 |
| `BACKEND_URL` | `http://backend:8080` | 백엔드 서버 주소 (Docker 내부 네트워크) |

## 테스트 방법

1. 워크플로우를 Import한 후 **Manual Execution** (재생 버튼)으로 실행
2. 각 노드를 클릭하면 실행 결과를 확인할 수 있음
3. 정상 동작 확인 후 워크플로우를 **Active** 상태로 전환하면 스케줄 실행 시작

## 운영 원칙

- User-Agent: `YouthFit-Bot/1.0 (+https://youthfit.kr/bot)`
- 요청 간 3초 딜레이 (Rate Limit)
- 중복 제거는 백엔드에서 SHA-256 해시 기반으로 처리
- 민감값(API 키 등)은 환경변수로 관리, JSON에 포함 금지
