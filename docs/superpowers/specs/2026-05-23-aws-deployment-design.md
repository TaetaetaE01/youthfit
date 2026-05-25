# AWS 배포 인프라 설계 (youthfit.xyz)

작성일: 2026-05-23
대상 도메인: `youthfit.xyz` (가비아 구매 + Route 53 네임서버 등록 완료)
리전: `ap-northeast-2` (서울) — CloudFront 인증서만 `us-east-1`

## 1. 목표

흩어진 청년 정책 도우미 YouthFit 의 MVP 트래픽을 받을 수 있는 운영 인프라를 가비아 도메인 + AWS 위에 구축한다. **MVP 단계 비용 최적화·운영 단순성을 1순위**, 향후 ECS/다중 인스턴스 마이그레이션 여지를 2순위로 설계한다.

## 2. 제약 / 비목표

- **단일 prod 환경.** staging/dev 환경은 v1 이후에 분리한다.
- **무중단 배포는 비목표.** 수초~수십초 다운타임을 허용한다.
- **다중 인스턴스/오토스케일링 비목표.** 트래픽 증가 시 점진 마이그레이션 경로(ALB → ECS Fargate)는 열어두되 이번에는 도입하지 않는다.
- **공식 신청 포털 대체 비목표** — 도메인은 보완형 서비스를 위한 것.
- **모니터링·알람**은 v1 이후 별도 작업. 일단 CloudWatch 기본 메트릭만.

## 3. 현재 상태

| 항목 | 상태 |
|------|------|
| 가비아 도메인 `youthfit.xyz` | ✅ 구매 완료 |
| Route 53 호스팅 영역 | ✅ 생성, 가비아 NS 변경 완료 (`NS-604.AWSDNS-11.NET` 외 3개) |
| AWS 계정 (deploy target) | ✅ `379197597410` (별도 계정, 운영용) |
| Route 53 호스팅 영역 ID | ✅ `Z05811777WNU2LJAW6QF` |
| (참고) 기존 계정 `596776566549` | SES/S3 (BF-rest-S3) 실험용 — prod 배포에는 미사용 |
| 기존 사용 중인 AWS 서비스 | SES (도메인 미검증), S3 (`BF-rest-S3` 키로 첨부파일 업로드) |
| Spring Boot 백엔드 | docker-compose 로컬 가동 중 |
| Vite + React 프론트엔드 | 로컬 `npm run dev` 가동 중 |
| n8n 워크플로우 | docker-compose 로컬 가동 중 |
| Postgres (pgvector) | docker-compose 로컬 가동 중 |

## 4. 최종 아키텍처

```
                ┌──────────── Route 53 (youthfit.xyz) ────────────┐
                │ A  alias  youthfit.xyz     → CloudFront         │
                │ A  alias  www.xxx          → CloudFront         │
                │ A         api.xxx          → EC2 Elastic IP     │
                │ A         n8n.xxx          → EC2 Elastic IP     │
                │ TXT/CNAME  SES, DKIM, ACM 검증 토큰              │
                └─────────────────────────────────────────────────┘
                            │                           │
                            ▼                           ▼
                ┌─── us-east-1 ────┐         ┌──── ap-northeast-2 ────┐
                │  ACM 인증서       │         │                         │
                │  *.youthfit.xyz   │         │  EC2 t3.small (퍼블릭) │
                └────────┬─────────┘         │  ┌─────────────────┐    │
                         │                   │  │ Caddy :443/:80  │    │
                         ▼                   │  │  ├─ api → backend│   │
                ┌─── CloudFront ───┐         │  │  └─ n8n → n8n   │   │
                │  OAC + SPA cache │         │  │ backend:8080    │   │
                └────────┬─────────┘         │  │ redis (internal)│   │
                         │                   │  │ n8n:5678        │   │
                         ▼                   │  └─────────────────┘    │
                ┌─── S3 (private) ─┐         │           │              │
                │ youthfit-web-... │         │           │              │
                │ (Vite build)     │         │           ▼              │
                └──────────────────┘         │     RDS Postgres         │
                                             │     db.t3.micro          │
                                             │     (private subnet)     │
                                             │                          │
                                             │  SES (mail.youthfit.xyz) │
                                             │  ECR (backend image)     │
                                             │  SSM Parameter Store     │
                                             │  (시크릿 보관소)            │
                                             └──────────────────────────┘

GitHub Actions ──build──▶ ECR (backend:<sha>) ──SSH──▶ EC2: docker compose pull && up -d
```

### 컴포넌트 요약

| 컴포넌트 | 선택 | 핵심 이유 |
|---------|------|----------|
| 백엔드 컴퓨트 | EC2 t3.small (1대, 퍼블릭 서브넷, EIP 부착) | MVP 비용 최저, 현재 docker-compose 자산 그대로 |
| TLS 종료 (백엔드) | Caddy (Let's Encrypt 자동) | ALB 월 $20 절약, 설정 4줄 |
| 컨테이너 구성 | docker-compose.prod.yml (backend, redis, n8n, caddy) | 로컬 dev 와 구조 일치 |
| 데이터베이스 | RDS Postgres 17 (db.t3.micro, Single-AZ, pgvector 확장) | 백업·장애조치 매니지드 |
| 정적 호스팅 | S3 (private) + CloudFront (OAC) | SPA 전용 비용 최저 |
| TLS (프론트) | ACM `us-east-1` `*.youthfit.xyz` | CloudFront 강제 |
| DNS | Route 53 (호스팅 영역 이미 생성) | 가비아 NS 변경 완료 |
| 이메일 | SES `mail.youthfit.xyz` MAIL FROM + DKIM | `EMAIL_TRANSPORT=ses` 활성 |
| 이미지 레지스트리 | ECR `youthfit-backend` | EC2 IAM role 로 pull |
| CI/CD | GitHub Actions → ECR push → SSH 배포 | 단순성 우선 |
| 시크릿 | SSM Parameter Store + EC2 IAM 인스턴스 프로파일 | `.env` 평문 회피 |
| IaC | Terraform | 재현·롤백 |

## 5. 상세 설계

### 5.1 네트워크

- **VPC** 1개 (`youthfit-prod-vpc`, CIDR `10.20.0.0/16`)
- **퍼블릭 서브넷** 2개 (서로 다른 AZ, 향후 ALB 도입 대비) — EC2, 향후 ALB 배치
  - `10.20.1.0/24` (ap-northeast-2a)
  - `10.20.2.0/24` (ap-northeast-2c)
- **프라이빗 서브넷** 2개 (RDS 다중 AZ subnet group 요건) — RDS만
  - `10.20.11.0/24` (ap-northeast-2a)
  - `10.20.12.0/24` (ap-northeast-2c)
- **NAT Gateway 없음** — RDS 가 외부 인터넷 필요 없고, EC2 는 퍼블릭 서브넷. (월 ~$32 절약)
- **Internet Gateway** 1개
- **보안 그룹**
  - `youthfit-web-sg` (EC2): 22(SSH, 내 IP만), 80, 443 inbound from 0.0.0.0/0
  - `youthfit-db-sg` (RDS): 5432 inbound only from `youthfit-web-sg`
- **Elastic IP** 1개 → EC2 부착 (Route 53 A 레코드 대상)

### 5.2 컴퓨트 (EC2)

- **인스턴스 유형**: t3.small (2 vCPU, 2 GB RAM) — 백엔드 + redis + n8n + caddy 합쳐 RAM ~1.5 GB 예상. 부족하면 t3.medium 으로 즉시 업그레이드.
- **AMI**: Amazon Linux 2023 (최신)
- **루트 볼륨**: gp3 30 GB
- **사용자 데이터**: cloud-init 로 Docker, docker compose plugin, AWS CLI v2, CloudWatch agent 설치 + ECR 로그인 helper
- **IAM 인스턴스 프로파일** (`youthfit-ec2-role`):
  - `AmazonEC2ContainerRegistryReadOnly` (ECR pull)
  - `AmazonSSMReadOnlyAccess` + Parameter Store 경로 한정 인라인 정책 (`/youthfit/prod/*` 읽기)
  - `AmazonSESFullAccess` (백엔드가 SES 발송)
  - `AmazonS3FullAccess` (첨부 파일 버킷 한정 인라인 정책으로 좁힘)
- **SSH 키**: Terraform 으로 key pair 등록. 사용자가 직접 ed25519 키 발급 후 공개키만 입력.
- **백업**: EBS snapshot 일 1회 (AWS Backup, 7일 보관)

### 5.3 docker-compose.prod.yml

로컬 `docker-compose.yml` 과 분리. 차이점:

```yaml
services:
  # postgres 제거 — RDS 사용
  caddy:
    image: caddy:2-alpine
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy-data:/data
      - caddy-config:/config
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    # ports 미공개 (외부 노출 안 함)
    volumes:
      - redis-data:/data
    restart: unless-stopped

  n8n:
    image: n8nio/n8n:latest
    environment:
      - WEBHOOK_URL=https://n8n.youthfit.xyz/
      - N8N_HOST=n8n.youthfit.xyz
      - N8N_PROTOCOL=https
      # 나머지는 .env 또는 SSM 에서 주입
    volumes:
      - n8n-data:/home/node/.n8n
    restart: unless-stopped

  backend:
    image: ${ECR_REGISTRY}/youthfit-backend:${IMAGE_TAG:-latest}
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: ${RDS_ENDPOINT}
      # 나머지는 .env (SSM 에서 fetch 한 결과) 에서 주입
    restart: unless-stopped
```

### 5.4 Caddyfile

```
api.youthfit.xyz {
    reverse_proxy backend:8080
    encode gzip zstd
    log {
        output file /data/access.log
        format json
    }
}

n8n.youthfit.xyz {
    basicauth {
        admin <bcrypt-hash>
    }
    reverse_proxy n8n:5678
}
```

n8n 은 자체 BasicAuth 도 켜져 있으나, Caddy 단에서도 한 번 더 막아 이중 방어.

### 5.5 데이터베이스 (RDS)

- **엔진**: Postgres 17 (현재 pgvector/pgvector:pg17 이미지와 동일 메이저)
- **인스턴스 클래스**: db.t3.micro (1 vCPU, 1 GB RAM)
- **스토리지**: 20 GB gp3, autoscaling enabled (max 100 GB)
- **Single-AZ** (Multi-AZ 는 v1 이후)
- **Subnet group**: 프라이빗 서브넷 2개로 구성
- **Security group**: `youthfit-db-sg` (5432 from `youthfit-web-sg` only)
- **Parameter group**: 커스텀 (`shared_preload_libraries=pgvector` 명시)
- **pgvector 확장**: 인스턴스 생성 후 `CREATE EXTENSION vector;` 수동 실행
- **백업**: 자동 7일 보관, 매일 03:00 KST
- **스키마 초기화**: 기존 docker-compose 의 `./db/init` 디렉터리 SQL 을 RDS 에 1회 수동 적용 (Flyway 미사용 정책 유지)

### 5.6 정적 호스팅 (S3 + CloudFront)

- **S3 버킷**: `youthfit-web-prod` (`us-east-1` 이 아닌 `ap-northeast-2`. CloudFront 가 글로벌 캐싱이라 origin 리전 영향 미미)
  - Public access **차단**. CloudFront OAC 만 GetObject 허용
  - 버전 관리 활성화 (Vite build artifact 보존)
- **CloudFront 배포**
  - Origin: S3 버킷 (OAC)
  - Default behavior: `index.html` 외 캐싱 1년, `index.html` 은 no-cache
  - SPA fallback: 403/404 → `/index.html` 200 (Custom error responses)
  - Alternate domain names: `youthfit.xyz`, `www.youthfit.xyz`
  - SSL certificate: ACM `us-east-1` `*.youthfit.xyz` + `youthfit.xyz`
  - 압축: gzip + brotli on
  - HTTP/2, HTTP/3 활성
- **배포**: GitHub Actions 가 `vite build` 결과를 S3 sync + CloudFront invalidation `/*`

### 5.7 DNS 레코드 계획 (Route 53)

| 이름 | 타입 | 값 | 목적 |
|-----|-----|-----|------|
| `youthfit.xyz` | A (Alias) | CloudFront 도메인 | 메인 |
| `www.youthfit.xyz` | A (Alias) | CloudFront 도메인 | www |
| `youthfit.xyz` | AAAA (Alias) | CloudFront 도메인 | IPv6 |
| `www.youthfit.xyz` | AAAA (Alias) | CloudFront 도메인 | IPv6 |
| `api.youthfit.xyz` | A | EC2 Elastic IP | 백엔드 API |
| `n8n.youthfit.xyz` | A | EC2 Elastic IP | n8n |
| `_<acm-token>.youthfit.xyz` | CNAME | ACM 검증 토큰 | 인증서 검증 |
| `_amazonses.mail.youthfit.xyz` | TXT | SES 검증 토큰 | SES |
| `<dkim-token>._domainkey.mail.youthfit.xyz` | CNAME | SES DKIM | 이메일 서명 |
| `mail.youthfit.xyz` | MX | `10 feedback-smtp.ap-northeast-2.amazonses.com` | SES MAIL FROM |
| `mail.youthfit.xyz` | TXT | `v=spf1 include:amazonses.com ~all` | SPF |
| `_dmarc.youthfit.xyz` | TXT | `v=DMARC1; p=none; rua=mailto:dmarc@...` | DMARC (선택) |

> Caddy 의 Let's Encrypt 챌린지는 **HTTP-01** 으로 진행 (포트 80 인바운드 허용 SG 이용). DNS-01 챌린지는 IAM 권한 위임이 추가로 필요하므로 v1 에선 사용하지 않는다.

### 5.8 TLS

- **CloudFront 용**: ACM `us-east-1` 에 `youthfit.xyz` + `*.youthfit.xyz` 단일 인증서. DNS 검증.
- **EC2 (api, n8n)용**: Caddy 가 Let's Encrypt 에서 자동 발급. EC2 SG 가 80/443 inbound 열려있어야 함.
- **redirect**: Caddy 가 자동으로 HTTP → HTTPS 301.

### 5.9 SES 도메인 검증

- 발신 도메인: **`mail.youthfit.xyz`** (subdomain, MAIL FROM 분리 ─ AWS 권장)
- 발신 주소: `no-reply@mail.youthfit.xyz` (또는 `notifications@`)
- 검증: SES 콘솔 → Verify a new domain → MAIL FROM 도메인 `mail.youthfit.xyz` 지정 → Route 53 자동 레코드 등록 옵션 사용
- 현재 sandbox 일 가능성 높음 → **prod release 전 sandbox 해제 신청** 필수
- 발신 IAM 사용자 `youthfit-ses-sender` 는 OPS.md 절차대로 이미 만들 예정 (별도 키, 백엔드 EC2 IAM role 로 대체 가능 — 가능하면 IAM role 경로 우선)

### 5.10 이미지 레지스트리 (ECR)

- 레포지토리: `youthfit-backend` (private)
- 이미지 태그: `latest` + `<git-sha>` 듀얼 태그
- Lifecycle policy: 최근 10개 외 expire
- 스캔: Push 시 자동 (Basic scanning)

### 5.11 CI/CD (GitHub Actions)

#### `backend-deploy.yml`

- 트리거: `main` 브랜치 push + `backend/**` 경로 변경 시
- 단계:
  1. `gradle build`
  2. Docker buildx → ECR push (`latest` + `<sha>`)
  3. SSH 접속 (Action: `appleboy/ssh-action`)
  4. EC2 에서 `aws ecr get-login-password | docker login`, `docker compose pull backend`, `docker compose up -d backend`
  5. health check (`curl https://api.youthfit.xyz/actuator/health`)
- 시크릿 (GitHub Secrets):
  - `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` (배포 전용 IAM 키, ECR push only)
  - `EC2_SSH_KEY`, `EC2_HOST`, `EC2_USER`

#### `frontend-deploy.yml`

- 트리거: `main` 브랜치 push + `frontend/**` 경로 변경
- 단계:
  1. `npm ci && npm run build`
  2. `aws s3 sync dist/ s3://youthfit-web-prod --delete`
  3. `aws cloudfront create-invalidation --paths "/*"`

### 5.12 시크릿 관리

- **AWS Systems Manager Parameter Store** 에 `/youthfit/prod/*` 경로로 저장
  - `/youthfit/prod/db/password`
  - `/youthfit/prod/openai/api-key`
  - `/youthfit/prod/jwt/secret`
  - `/youthfit/prod/kakao/client-secret`
  - `/youthfit/prod/internal/api-key`
  - `/youthfit/prod/n8n/basic-auth-password`
  - 기타 OPS.md 환경변수 전체
- **EC2 부팅 시점**에 `aws ssm get-parameters-by-path --path /youthfit/prod --with-decryption` 호출, `/etc/youthfit/.env` 로 dump, docker compose 가 이 env file 을 읽음
- SecureString 타입 사용 (KMS 기본 키)
- `.env` 평문 파일은 git 에도 EC2 디스크에도 영구 저장하지 않음 (EC2 디스크의 `/etc/youthfit/.env` 는 부팅 후 600 권한, root 소유)

### 5.13 도메인 / 도메인 매핑 검증 절차

- `dig api.youthfit.xyz @8.8.8.8` → EC2 EIP 일치
- `curl -I https://youthfit.xyz` → CloudFront 200
- `curl -I https://api.youthfit.xyz/actuator/health` → backend 200
- SES 콘솔에서 도메인 status = `Verified`
- 검증된 발신자 1명에게 dry-run 메일 발송 확인

## 6. 비용 추정 (월, USD)

| 항목 | 수량 | 비용 |
|------|------|------|
| EC2 t3.small | 1대 on-demand | ~$18 |
| EBS gp3 30GB | 1개 | ~$3 |
| EBS snapshot (7일 보관) | 일 1회 | ~$1 |
| Elastic IP | 1개 (attached) | $0 |
| RDS db.t3.micro Single-AZ | 1대 | ~$15 |
| RDS storage gp3 20GB | | ~$3 |
| RDS 백업 (7일) | | ~$2 |
| S3 (정적 자산, GB·요청 적음) | | ~$1 |
| CloudFront (트래픽 < 50GB) | | ~$1-3 |
| Route 53 호스팅 영역 | 1개 | $0.50 |
| Route 53 쿼리 | | ~$1 |
| 데이터 전송 (Outbound) | | ~$1-5 |
| ECR storage (5GB 미만) | | ~$0.50 |
| SSM Parameter Store | Standard tier | $0 |
| SES (월 ~10K건 이내) | | ~$1 |
| **합계 (예상)** | | **~$45-55** |

## 7. 단계별 실행 계획

> 각 Phase 는 별도 implementation plan 으로 분해 가능. 이 spec 은 큰 그림.

### Phase 0 — 사전 점검 (사용자 직접)
- IAM 사용자 `youthfit-deploy-admin` 생성 + AdministratorAccess + MFA + 액세스 키 발급
- 로컬 `~/.aws/credentials` 에 `[youthfit-deploy]` 프로파일 추가
- SSH ed25519 키 페어 생성 (`~/.ssh/youthfit_prod_ed25519`)
- GitHub Secrets 슬롯 사전 합의

### Phase 1 — Terraform 스켈레톤
- `infra/terraform/` 디렉터리 생성
- `providers.tf`, `versions.tf`, `backend.tf` (S3 + DynamoDB lock)
- Terraform state 용 S3 버킷 + DynamoDB 테이블만 먼저 생성 (bootstrap)

### Phase 2 — 네트워크
- VPC, 서브넷, IGW, route table, security groups, EIP
- `terraform apply`

### Phase 3 — RDS
- DB subnet group, parameter group, RDS 인스턴스
- 패스워드는 Terraform 입력 변수 → SSM Parameter Store 동시 저장
- pgvector 확장 수동 활성화
- 기존 `db/init/*.sql` + `backend/src/main/resources/sql/*.sql` 운영 PG 에 수동 적용

### Phase 4 — EC2 + Caddy + docker-compose.prod.yml
- EC2 IAM role / instance profile 정의
- EC2 인스턴스 + cloud-init user data
- `docker-compose.prod.yml`, `Caddyfile` 작성 (git 에 커밋)
- EC2 에 첫 수동 deploy

### Phase 5 — ECR + 첫 backend 이미지
- `Dockerfile` 검토 (이미 backend 에 존재)
- ECR 레포지토리 생성
- 로컬에서 첫 빌드/푸시
- EC2 에서 `docker compose pull && up -d` 검증

### Phase 6 — SSM Parameter Store
- 모든 환경변수 SSM 에 등록
- EC2 부팅 스크립트 (`/etc/youthfit/fetch-secrets.sh`) 작성
- systemd unit (`youthfit.service`) 으로 compose up 자동화

### Phase 7 — S3 + CloudFront + ACM
- ACM `us-east-1` 인증서 요청 (DNS 검증)
- S3 `youthfit-web-prod` 버킷, OAC, 버킷 정책
- CloudFront 배포
- 첫 `vite build` 결과 S3 sync

### Phase 8 — Route 53 레코드
- A/AAAA Alias → CloudFront
- A → EC2 EIP (api, n8n)
- ACM 검증 CNAME (Terraform 으로 자동)

### Phase 9 — SES 도메인 검증
- `mail.youthfit.xyz` 도메인 ID 생성
- DKIM CNAME, MAIL FROM TXT/MX 자동 등록
- Sandbox 해제 신청

### Phase 10 — GitHub Actions
- `backend-deploy.yml`, `frontend-deploy.yml`
- GitHub Secrets 등록
- 첫 자동 배포 dry-run

### Phase 11 — 검증 & 컷오버
- E2E 스모크 테스트 (도메인 핵심 플로우)
- 카카오 OAuth redirect URI 운영 도메인으로 변경
- `EMAIL_TRANSPORT=ses` 활성, dry-run 메일
- 운영 공개

### Phase 12 — 후속 정리
- AdministratorAccess 좁히기 (실제 사용 권한만)
- CloudWatch 알람 (CPU, RDS 디스크, 5xx)
- 일일 RDS snapshot 정책 재검토

## 8. 위험 / 트레이드오프

1. **단일 EC2 = SPoF**
   - 영향: api, n8n 모두 같이 다운
   - 완화: EBS snapshot 일 1회, AMI 정기 백업으로 빠른 복구. v1 이후 ALB+다중 인스턴스 검토.

2. **Single-AZ RDS = 장애조치 없음**
   - 영향: AZ 장애 시 1시간 이상 다운 가능
   - 완화: MVP 단계에선 자동 백업·PITR 로 충분. 사용자 1만 명 또는 매출 발생 시 Multi-AZ 전환.

3. **Caddy on EC2 vs ALB**
   - Caddy 가 LE 발급 실패 시 인증서 만료 가능
   - 완화: Caddy 자체 자동 재시도, 만료 30일 전 자동 갱신. CloudWatch 알람으로 `caddy` 컨테이너 health 모니터링 추가 가능.

4. **SES Sandbox**
   - prod release 전 해제 신청 필수. 해제 미승인 시 검증된 수신자에만 발송.
   - 완화: Phase 9 후 즉시 해제 신청. 일반적으로 1-3 영업일.

5. **n8n 워크플로우 데이터**
   - n8n 자체 SQLite (volume) 또는 Postgres
   - 현재 docker-compose 는 SQLite 디폴트 → EC2 EBS snapshot 으로 백업
   - v1 이후 RDS 의 별도 schema 로 이전 검토

6. **IaC 와 수동 작업 혼재**
   - 일부 작업은 콘솔에서만 가능 (SES sandbox 해제 신청 등)
   - 완화: README 에 수동 단계 명시, Terraform import 가능한 건 import.

## 9. 향후 확장 경로

| 트리거 | 액션 |
|--------|------|
| 트래픽 증가 (>1K req/s) | ALB 도입 → EC2 다중 인스턴스 |
| 운영 안정성 요구 ↑ | ECS Fargate 마이그레이션 (backend 만 먼저) |
| 매출 발생 | RDS Multi-AZ 전환, 백업 보관 기간 ↑ |
| 글로벌 사용자 | CloudFront 이미 글로벌. RDS read replica 검토 |
| 알람 강화 | CloudWatch + SNS → Slack |

## 10. 참고

- 가비아 → Route 53 연결: 이미 완료 (NS 변경)
- 관련 OPS 문서: `docs/OPS.md` (SES, SSM 키, 환경변수 슬롯)
- 백엔드 모듈 경계: `backend/CLAUDE.md`, `docs/ARCHITECTURE.md`
- 비용 안전장치: 정책 allowlist (`POLICY_ALLOWLIST`) 는 prod default 빈 값 (= 전체 허용) 유지

---

이 spec 이 승인되면 `superpowers:writing-plans` 스킬로 Phase 별 implementation plan 을 작성한다.
