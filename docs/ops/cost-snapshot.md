# YouthFit prod 인프라 스펙 + 비용 스냅샷

**기준일**: 2026-05-26
**리전**: `ap-northeast-2` (서울) — ACM(us-east-1) / CloudFront / Route 53 만 글로벌
**AWS 계정**: `379197597410`
**상태**: Plan A+B+C(인프라)+D 완료, Plan E (CI/CD) 대기 — 트래픽 미발생 idle 상태

> 가격은 2026-05 기준 서울 리전 on-demand 표준 요금. 1개월 = 730시간(연 평균) 환산.
> Free tier 효과는 포함하지 않은 풀가격 — 새 AWS 계정의 첫 12개월에 일부 항목 추가 절감 가능 (RDS db.t3.micro 750시간/월, S3 5GB 등).

---

## 1. 컴퓨트 / 스토리지

| # | 리소스 | 스펙 / 설정 | 식별자 |
|---|--------|-------------|--------|
| 1 | EC2 | `t3.small` (2 vCPU, 2 GB RAM) on-demand, AL2023, IMDSv2 required | `i-0781eaedf3330170e` |
| 2 | EBS root | gp3 30GB, encrypted, delete_on_termination | `youthfit-prod-web-root` |
| 3 | Elastic IP | attached to EC2 | `13.124.202.15` |

## 2. 데이터베이스

| # | 리소스 | 스펙 / 설정 | 식별자 |
|---|--------|-------------|--------|
| 4 | RDS | Postgres 17.10, `db.t3.micro` (1 vCPU, 1 GB RAM), Single-AZ, encrypted | `youthfit-prod` |
| 5 | RDS storage | gp3 20GB (autoscale max 100GB) | included in #4 |
| 6 | RDS 자동 백업 | 7일 retention, KST 03:00-04:00 window | included in #4 |
| 7 | RDS extensions | `vector 0.8.2`, `pg_trgm` | runtime |

## 3. 네트워크

| # | 리소스 | 스펙 / 설정 | 식별자 |
|---|--------|-------------|--------|
| 8 | VPC | `10.20.0.0/16` | `vpc-0db5dbb6f4edda312` |
| 9 | Public subnet | 2개 (ap-northeast-2a/2c) | EC2 배치 |
| 10 | Private subnet | 2개 (ap-northeast-2a/2c) | RDS subnet group |
| 11 | Internet Gateway | 1개 | public 라우팅 |
| 12 | Security Groups | web (22/80/443), db (5432 from web) | `sg-0bb16d435e9d3f271`, `sg-0e3edcbe2c57a58e1` |
| 13 | NAT Gateway | **없음** (의도적 비용 절약 — 월 ~$32 절감) | n/a |

## 4. 프론트엔드 / CDN

| # | 리소스 | 스펙 / 설정 | 식별자 |
|---|--------|-------------|--------|
| 14 | S3 web bucket | private, versioning, AES256, BucketOwnerEnforced | `youthfit-web-prod` (현재 비어있음, Vite build artifact 대기) |
| 15 | CloudFront | OAC SigV4, SPA fallback (403/404→`/index.html`), HTTP/2+3, PriceClass_200 | `E117R7JX6SV3LC` (`d1z1wyrsupsvug.cloudfront.net`) |
| 16 | ACM 인증서 | us-east-1, `youthfit.xyz` + `*.youthfit.xyz`, 만료 2026-12-09 | `arn:aws:acm:us-east-1:.../ffc1b511-...` |

## 5. DNS

| # | 리소스 | 스펙 / 설정 | 식별자 |
|---|--------|-------------|--------|
| 17 | Route 53 hosted zone | `youthfit.xyz` | `Z05811777WNU2LJAW6QF` |
| 18 | Route 53 레코드 | apex/www A+AAAA Alias → CloudFront, api A → EIP. n8n 미생성 | 5개 |

## 6. 컨테이너 / 시크릿

| # | 리소스 | 스펙 / 설정 | 식별자 |
|---|--------|-------------|--------|
| 19 | ECR | `youthfit-backend` (MUTABLE, scan_on_push, AES256, lifecycle: untagged 7d / sha-tagged 10개) | `379197597410.dkr.ecr.ap-northeast-2.amazonaws.com/youthfit-backend` (현재 이미지 0개) |
| 20 | SSM Parameter Store | 10 슬롯 (6 SecureString + 4 String), Standard tier | `/youthfit/prod/*` |
| 21 | IAM EC2 role + profile | ECR pull + SSM `/youthfit/prod/*` + SES send | `youthfit-ec2-role`, `youthfit-ec2-profile` |

## 7. Terraform state

| # | 리소스 | 스펙 / 설정 | 식별자 |
|---|--------|-------------|--------|
| 22 | S3 state bucket | versioning, SSE-S3, public access block | `youthfit-tfstate-prod` |
| 23 | DynamoDB lock | PAY_PER_REQUEST, 거의 미사용 | `youthfit-tfstate-lock` |

---

## 비용 표 (현재 idle 상태)

| 리소스 | 시간당 | 일 (×24) | 주 (×168) | 월 (×730) |
|--------|--------:|---------:|----------:|----------:|
| EC2 t3.small (1대) | $0.02600 | $0.624 | $4.368 | $18.98 |
| EBS gp3 30GB | $0.00395 | $0.095 | $0.663 | $2.88 |
| RDS db.t3.micro | $0.02600 | $0.624 | $4.368 | $18.98 |
| RDS gp3 storage 20GB | $0.00315 | $0.076 | $0.529 | $2.30 |
| RDS 백업 (~1GB 내 무료) | $0.00000 | $0.000 | $0.000 | $0.00 |
| Elastic IP (attached) | $0.00000 | $0.000 | $0.000 | $0.00 |
| S3 (state + web, < 1GB) | $0.00003 | $0.001 | $0.005 | $0.025 |
| DynamoDB (idle) | $0.00001 | $0.000 | $0.001 | $0.005 |
| CloudFront (트래픽 0) | $0.00000 | $0.000 | $0.000 | $0.00 |
| Route 53 hosted zone | $0.00068 | $0.016 | $0.115 | $0.50 |
| Route 53 쿼리 (idle) | $0.00000 | $0.000 | $0.000 | $0.00 |
| ACM 인증서 | $0.00000 | $0.000 | $0.000 | $0.00 |
| ECR storage (0 이미지) | $0.00000 | $0.000 | $0.000 | $0.00 |
| SSM Standard tier | $0.00000 | $0.000 | $0.000 | $0.00 |
| CloudWatch 기본 메트릭 | $0.00000 | $0.000 | $0.000 | $0.00 |
| IAM | $0.00000 | $0.000 | $0.000 | $0.00 |
| **합계 (idle)** | **~$0.0599** | **~$1.44** | **~$10.05** | **~$43.67** |

> 참고: Plan B 종료 시 시간당 ~$0.029(월 ~$21) → Plan C 후 ~$0.056(월 ~$40) → **Plan D 후 ~$0.060(월 ~$44)**.

---

## 비용 시나리오 (Plan E 가동 후 가정)

서비스가 실제로 가동되어 사용자 트래픽이 발생하면 다음 항목이 추가됩니다.

### 시나리오 A: 초기 운영 (트래픽 < 50GB/월)

| 추가 항목 | 가정 | 월 추가 비용 |
|----------|------|-------------:|
| CloudFront outbound (50GB) | 사용자 트래픽 | ~$4.25 |
| S3 web bucket (Vite build ~50MB + 버전) | 정적 자산 | ~$0.005 |
| ECR storage (이미지 3-5GB) | backend 이미지 보관 | ~$0.40 |
| Route 53 쿼리 (~1M/월) | DNS lookup | ~$0.40 |
| RDS 백업 초과분 (~2GB) | 누적 후 무료 한도 초과 시 | ~$0.20 |
| EC2 outbound (작음) | API 응답 | ~$0.50 |
| **합계 (시나리오 A)** | | **~$49.42** |

### 시나리오 B: 적당 운영 (트래픽 200GB/월)

| 추가 항목 | 가정 | 월 추가 비용 |
|----------|------|-------------:|
| CloudFront outbound (200GB) | 사용자 1만명 수준 | ~$17 |
| S3 storage / 요청 | 빈도 증가 | ~$0.10 |
| ECR storage (5-10GB) | | ~$0.80 |
| Route 53 쿼리 (~5M/월) | | ~$2.00 |
| RDS 백업 초과분 | DB 크기 증가 | ~$1.00 |
| EC2 outbound | | ~$2.00 |
| **합계 (시나리오 B)** | | **~$66.57** |

### 시나리오 C: 트래픽 증가 시 점진 마이그레이션 트리거 (월 $80+)

- CloudFront > 500GB/월 → 트래픽 비용 ~$40+/월
- RDS db.t3.micro CPU credit 고갈 → `db.t3.small` 또는 Multi-AZ 검토 (+$20-40/월)
- EC2 단일 인스턴스 SPoF 우려 → ALB + 2nd EC2 (+$25/월 ALB)

> MVP 단계는 시나리오 A 가 목표. 시나리오 B 도달 시 모니터링 강화 (Plan F), C 도달 시 아키텍처 재검토 (spec `docs/superpowers/specs/2026-05-23-aws-deployment-design.md` 의 §9 확장 경로).

---

## 비용 최적화 메모

- ✅ **NAT Gateway 없음**: 월 ~$32 절감 (RDS 가 인터넷 불필요, EC2 는 public subnet 직접)
- ✅ **CloudFront PriceClass_200**: 남미/아프리카 제외, ~10-15% 절감
- ✅ **RDS Single-AZ**: Multi-AZ 대비 50% 절감 (~$15/월). MVP 단계 허용
- ✅ **ALB 없음**: Caddy on EC2 로 TLS 직접 처리, 월 ~$20 절감
- ✅ **AWS managed CloudFront 정책**: 별도 custom policy 비용 없음
- ✅ **SSM Standard tier**: Advanced tier 미사용 (Standard 무료, 슬롯 10K 까지)
- ✅ **EIP attached**: idle EIP $0.005/시간 vs attached $0 — 항상 EC2 부착 유지

## 일회성 비용

| 항목 | 금액 |
|------|------|
| 가비아 도메인 `youthfit.xyz` (1년) | 사용자가 별도 지불 |
| AWS 계정 생성 | $0 |
| 기타 | $0 |

## 결제 / 알람

- AWS Billing 대시보드: 가입 즉시 활성화 권장
- 월 $50 / $75 / $100 알람 권장 (CloudWatch + SNS, **Plan F 에서 구성 예정**)
- 현재까지 청구 누적은 콘솔 → Billing → Bills 에서 확인

## 변경 로그

| 일자 | 변경 | 시간당 변화 | 월 변화 |
|------|------|------------:|--------:|
| 2026-05-23 | Plan A 종료 (인프라 자원 없음) | $0 | $0 |
| 2026-05-23 | Plan B 종료 (VPC + RDS) | +$0.029 | +$21 |
| 2026-05-26 | Plan C 종료 (EC2 추가) | +$0.027 | +$19 |
| **2026-05-26** | **Plan D 종료 (S3/CF/Route53/ACM)** | **+$0.004** | **+$3** |
| (예정) | Plan E 종료 (서비스 가동) | +$5-10 트래픽 따라 | +$5-25 |

---

## 관련 문서

- 인프라 설계: [`docs/superpowers/specs/2026-05-23-aws-deployment-design.md`](../superpowers/specs/2026-05-23-aws-deployment-design.md)
- Plan A: prereqs (manual)
- Plan B: [`docs/superpowers/plans/2026-05-23-aws-deployment-plan-b-network-db.md`](../superpowers/plans/2026-05-23-aws-deployment-plan-b-network-db.md)
- Plan C: [`docs/superpowers/plans/2026-05-23-aws-deployment-plan-c-ec2-backend.md`](../superpowers/plans/2026-05-23-aws-deployment-plan-c-ec2-backend.md)
- Plan D: [`docs/superpowers/plans/2026-05-23-aws-deployment-plan-d-frontend-domain.md`](../superpowers/plans/2026-05-23-aws-deployment-plan-d-frontend-domain.md)
- 운영 환경 변수: [`docs/OPS.md`](../OPS.md)
- Terraform code: [`infra/terraform/`](../../infra/terraform/)
