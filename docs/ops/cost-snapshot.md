# YouthFit prod 인프라 스펙 + 비용 스냅샷

**기준일**: 2026-05-26
**리전**: `ap-northeast-2` (서울) — ACM(us-east-1) / CloudFront / Route 53 만 글로벌
**AWS 계정**: `379197597410`
**상태**: Plan A+B+C(인프라)+D 완료, Plan E (CI/CD + 서비스 가동) 대기 — 트래픽 미발생 idle 상태

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
| 13 | NAT Gateway | **없음** (의도적 비용 절약 — 시간당만으로도 월 ~$45 절감, 데이터 처리비 별도) | n/a |

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
| RDS gp3 storage 20GB | $0.00315 | $0.076 | $0.529 | $2.29 |
| RDS 백업 (DB 크기 20GB까지 무료) | $0.00000 | $0.000 | $0.000 | $0.00 |
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
| **합계 (idle)** | **~$0.0599** | **~$1.44** | **~$10.05** | **~$43.66** |

> 라인별 4-5자리 반올림 누적으로 `시간당 × 730` 과 `월 합계` 사이에 ±$0.10 정도 오차가 날 수 있다. 청구 정확치는 항상 Billing 대시보드 기준.
>
> 참고: Plan B 종료 시 시간당 ~$0.029(월 ~$21) → Plan C 후 ~$0.054(월 ~$43) → **Plan D 후 ~$0.060(월 ~$44)**.

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
| RDS 백업 (DB 가 20GB 초과 시) | 누적 후 무료 한도 초과 시 | ~$0 (현재 < 1GB, 한참 여유) |
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

> MVP 단계는 시나리오 A 가 목표. 시나리오 B 도달 시 모니터링 강화 (Plan F), C 도달 시 아키텍처 재검토 (관련 설계 spec 은 후속 PR 에서 합류).

---

## AWS 서비스별 상세 단가 & 계산

> 2026-05 기준 서울 리전 (`ap-northeast-2`) on-demand 표준 요금. 1개월 = 730시간.
> AWS 가격은 자주 바뀌지 않지만 정확한 청구는 항상 [AWS Pricing Calculator](https://calculator.aws/) / Billing 대시보드로 확인.

### 1. EC2 (Elastic Compute Cloud)

| 항목 | 단가 (Seoul) | 우리 사용 | 월 비용 |
|------|-------------|-----------|--------:|
| `t3.small` Linux on-demand | $0.0260/시간 | 1대 × 730h | **$18.98** |
| 데이터 전송 (Outbound to Internet) | $0/GB (첫 100GB 무료) → $0.126/GB | 현재 0 | $0 |

- **CPU 크레딧**: t3 burstable. 평균 CPU < 20% 면 무료, 초과 시 $0.05/vCPU-시간
- **Free tier**: t2.micro/t3.micro 만 12개월 무료. **t3.small 은 ❌**
- **변동 요인**: 인스턴스 끄면 즉시 과금 중지. 단 EBS 는 계속 청구

### 2. EBS (Elastic Block Store)

| 항목 | 단가 (Seoul) | 우리 사용 | 월 비용 |
|------|-------------|-----------|--------:|
| gp3 storage | $0.0960/GB-월 | 30GB (EC2 root) | **$2.88** |
| gp3 IOPS (3000 기본 무료) | $0.0048/IOPS-월 | 0 추가 | $0 |
| gp3 throughput (125 MB/s 기본 무료) | $0.0384/MB/s-월 | 0 추가 | $0 |
| Snapshot | $0.05/GB-월 | 현재 0 | $0 |

- **Free tier**: 30GB gp2/gp3 12개월 무료
- 인스턴스 삭제해도 EBS 는 별도 — `delete_on_termination=true` 로 설정해뒀음

### 3. RDS (Relational Database Service)

| 항목 | 단가 (Seoul) | 우리 사용 | 월 비용 |
|------|-------------|-----------|--------:|
| `db.t3.micro` Postgres Single-AZ | $0.0260/시간 | 1대 × 730h | **$18.98** |
| gp3 storage | $0.1145/GB-월 | 20GB | **$2.29** |
| 자동 백업 (DB 크기까지 무료) | $0.095/GB-월 (초과분) | 현재 ~0.1GB | $0 |
| 데이터 전송 (in-VPC) | $0 | EC2↔RDS 만 | $0 |

- **Free tier**: db.t3.micro 750h/월 12개월 + 20GB gp2 (gp3 는 ❌)
- **Multi-AZ 전환 시**: +100% (×2 instance fee)
- **db.t3.small 업그레이드**: +$19/월 (= $0.052/시간)

### 4. EIP (Elastic IP)

| 항목 | 단가 | 우리 사용 | 월 비용 |
|------|------|-----------|--------:|
| attached (instance 부착) | $0 | 1개 attached | **$0** |
| idle (미부착) | $0.005/시간 | 0 | $0 |

- ⚠️ EC2 정지 시 EIP 가 attached 상태여도 과금됨 (인스턴스가 running 이어야 무료)
- 우리: EC2 24/7 가동 + EIP 부착 → 항상 무료

### 5. S3 (Simple Storage Service)

| 항목 | 단가 (Seoul) | 우리 사용 | 월 비용 |
|------|-------------|-----------|--------:|
| Standard storage | $0.025/GB-월 (첫 50TB) | state ~0.05MB + web 0B | ~$0.001 |
| PUT/COPY/POST/LIST 요청 | $0.0055/1000 | terraform apply 시 ~50 ops | <$0.001 |
| GET/SELECT 요청 | $0.00044/1000 | ~10 ops | <$0.001 |
| Data transfer out (Internet) | $0.126/GB | 0 (CloudFront 가 cache) | $0 |

- **버킷 2개**: `youthfit-tfstate-prod` (state), `youthfit-web-prod` (Vite build 대기)
- **Free tier**: 5GB + 20K GET + 2K PUT 첫 12개월
- **버전 관리**: 옛 버전 저장도 storage 비용 카운트

### 6. CloudFront

| 항목 | 단가 (글로벌, Asia tier) | 우리 사용 | 월 비용 |
|------|--------------------------|-----------|--------:|
| Data transfer out (Asia) | $0.085/GB (첫 10TB) | 현재 0 | $0 |
| HTTPS 요청 | $0.0075/10,000 (Asia) | 현재 0 | $0 |
| Invalidation (첫 1000 path/월) | 무료, 초과분 $0.005/path | 0 | $0 |

- **Free tier**: 1TB outbound + 10M requests + 2M Function invocations 12개월
- **PriceClass_200 적용**: 남미/아프리카 edge 제외, ~10-15% 절감
- **변동 요인**: 사용자 트래픽 비례. 50GB/월 = ~$4.25, 200GB/월 = ~$17, 1TB/월 = ~$85

### 7. Route 53

| 항목 | 단가 | 우리 사용 | 월 비용 |
|------|------|-----------|--------:|
| Hosted zone (첫 25개) | $0.50/월/zone | 1 zone | **$0.50** |
| Hosted zone (25개 초과) | $0.10/월/zone | 0 | $0 |
| Standard queries (첫 10억) | $0.40/M | 현재 ~0 | <$0.01 |
| Alias query to AWS (CloudFront, EIP, ALB) | **무료** | apex/www/api alias | $0 |
| Latency-based queries | $0.60/M | 미사용 | $0 |
| Health checks | $0.50~$0.75/check-월 | 미사용 (Plan F) | $0 |

- Alias 가 무료라는 게 큰 이점 — apex/www/api 모두 alias, 따라서 일반 쿼리 비용은 거의 0
- **변동 요인**: 트래픽이 늘면 standard query 비용 발생 (50K 도메인 lookup/일 = ~$0.60/월)

### 8. ACM (Certificate Manager)

| 항목 | 단가 | 우리 사용 | 월 비용 |
|------|------|-----------|--------:|
| Public 인증서 (AWS 서비스에 attach) | **무료** | 1개 (CloudFront) | $0 |
| Private CA | $400/월 | 미사용 | $0 |

- 와일드카드 / SAN / 자동 갱신 모두 무료
- 단, AWS 서비스에 attach 되지 않은 인증서는 ACM 자체 무료지만 갱신 안 됨

### 9. ECR (Elastic Container Registry)

| 항목 | 단가 (Seoul) | 우리 사용 | 월 비용 |
|------|-------------|-----------|--------:|
| Storage | $0.10/GB-월 | 현재 0 이미지 | $0 |
| Data transfer in | 무료 | (push 시) | $0 |
| Data transfer out (같은 region EC2) | 무료 | EC2 가 pull | $0 |
| Data transfer out (다른 region) | $0.09/GB | 0 | $0 |
| Vulnerability scanning (basic) | 무료 | scan_on_push | $0 |
| Enhanced scanning (Inspector) | $0.09/image-월 | 미사용 | $0 |

- **Free tier**: 500MB private storage 12개월
- **변동 요인**: 이미지 10개 × 400MB = 4GB → $0.40/월
- Spring Boot fat jar + JRE 베이스 = 보통 250-400MB 압축

### 10. SSM Parameter Store

| 항목 | 단가 | 우리 사용 | 월 비용 |
|------|------|-----------|--------:|
| Standard tier (10K 슬롯, 4KB/슬롯, 표준 처리량) | **무료** | 10 슬롯 | $0 |
| Advanced tier | $0.05/슬롯-월 + $0.05/10K API calls | 미사용 | $0 |
| API throughput tier (40 → 1000 ops/s) | $0.05/M ops | Standard 한도 내 | $0 |

- 우리는 Standard 한도 안: 10 슬롯 << 10K 한도
- SecureString 의 KMS 호출은 별도 (다음 항목)

### 11. KMS (Key Management Service)

| 항목 | 단가 | 우리 사용 | 월 비용 |
|------|------|-----------|--------:|
| Customer-managed CMK | $1/키-월 | 0 (AWS-managed 사용) | $0 |
| AWS-managed key (`aws/ssm`, `aws/s3`, `aws/rds` 등) | **무료** | 사용 중 | $0 |
| Encrypt/Decrypt API calls (첫 20K 무료) | $0.03/10K | <100 ops | <$0.01 |

- 우리는 명시적 CMK 안 만들고 `aws/ssm`, `aws/rds`, `aws/ebs` (default) 사용 → 비용 없음
- ⚠️ 명시적 CMK 만들면 키 하나당 월 $1 부과

### 12. DynamoDB (Terraform state lock)

| 항목 | 단가 | 우리 사용 | 월 비용 |
|------|------|-----------|--------:|
| PAY_PER_REQUEST write | $1.25/M | ~10-20/세션 | <$0.001 |
| PAY_PER_REQUEST read | $0.25/M | 비슷 | <$0.001 |
| Storage | $0.25/GB-월 | <1KB | $0 |

- **Free tier**: 25GB + 25 WCU/RCU 영구 무료 (PAY_PER_REQUEST 도 어느 정도 cover)
- 단순 lock 용도라 실질 비용 $0

### 13. CloudWatch

| 항목 | 단가 | 우리 사용 | 월 비용 |
|------|------|-----------|--------:|
| 기본 메트릭 (5분 간격) | 무료 | EC2/RDS/CloudFront 기본 | $0 |
| Custom 메트릭 | $0.30/메트릭-월 (첫 10K) | 0 | $0 |
| Logs ingestion | $0.50/GB | 0 (Plan F 에서 backend log 전송 검토) | $0 |
| Logs storage | $0.03/GB-월 | 0 | $0 |
| Logs Insights query | $0.005/GB scanned | 0 | $0 |
| Alarms (첫 10개 무료) | $0.10/alarm-월 (초과분) | 0 (Plan F) | $0 |

- **Plan F 도입 시**: CPU/RDS disk/5xx 알람 3-5개 → 무료 한도 내

### 14. IAM / STS

| 항목 | 단가 | 우리 사용 | 월 비용 |
|------|------|-----------|--------:|
| 모든 IAM 리소스 (user, role, policy) | **무료** | 1 user + 1 role + policies | $0 |
| STS API | 무료 | EC2 가 assume role | $0 |

### 15. VPC / Network 기초

| 항목 | 단가 | 우리 사용 | 월 비용 |
|------|------|-----------|--------:|
| VPC, Subnet, Route Table, IGW | **무료** | 1 VPC + 4 subnets + 1 IGW | $0 |
| Security Group, NACL | **무료** | 2 SGs | $0 |
| NAT Gateway | $0.062/시간 + $0.045/GB | **없음 (의도적 회피)** | **$0** |
| VPC Endpoint (Interface) | $0.014/시간 + $0.01/GB | 0 | $0 |
| VPC Endpoint (Gateway, S3/DynamoDB) | 무료 | 0 | $0 |
| VPC Peering | 0.01/GB (cross-AZ) | 0 | $0 |
| Transit Gateway | $0.07/시간 + $0.02/GB | 0 | $0 |

- NAT Gateway 회피 = 월 ~$45 절감 (시간당 $0.062 × 730h = $45.26, 데이터 처리비 $0.045/GB 별도)
- private subnet 의 RDS 는 인터넷 접근 불필요해서 NAT 안 둠

### 16. 데이터 전송 (Cross-AZ / Cross-Region / Internet)

| 항목 | 단가 (Seoul) | 우리 사용 | 월 비용 |
|------|-------------|-----------|--------:|
| EC2 ↔ RDS (같은 AZ) | $0 | 모두 같은 region | $0 |
| EC2 ↔ RDS (cross-AZ in VPC) | $0.01/GB 양방향 | 가능성 (RDS subnet group 이 2 AZ) | <$0.10 |
| EC2 → Internet (첫 100GB) | $0 | <100GB | $0 |
| EC2 → Internet (100GB 초과) | $0.126/GB | 거의 0 | $0 |
| RDS → Internet | $0.126/GB | 미사용 (private subnet) | $0 |

- RDS 가 EC2 와 같은 AZ 면 cross-AZ 비용도 0. 우리 RDS subnet group 은 2a/2c 둘 다 포함이라 RDS 가 어디 launch 됐는지에 따라 다름 (사전 확인 어렵). 실 사용량 적어 영향 미미.

---

## 청구 총합 재확인

```
EC2 t3.small         $18.98
RDS db.t3.micro      $18.98
EBS gp3 30GB         $2.88
RDS gp3 20GB         $2.29
Route 53 zone        $0.50
S3 / DynamoDB        $0.03
─────────────────────────
합계                  $43.66  (현재 idle)
```

---

## 비용 최적화 메모

- ✅ **NAT Gateway 없음**: 월 ~$45 절감 (RDS 가 인터넷 불필요, EC2 는 public subnet 직접)
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
| 2026-05-23 | Plan B 종료 (VPC + RDS + storage) | +$0.0291 | +$21.27 |
| 2026-05-26 | Plan C 종료 (EC2 + EBS root 30GB 추가) | +$0.0300 | +$21.86 |
| **2026-05-26** | **Plan D 종료 (S3/CF/Route53/ACM)** | **+$0.0008** | **+$0.53** |
| (예정) | Plan E 종료 (CI/CD + 서비스 가동) | +$5-10 트래픽 따라 | +$5-25 |

---

## 관련 문서

> 다음 문서들은 후속 PR 에서 같은 저장소에 합류 예정. 현재 main 에는 아직 없음.
>
> - 인프라 설계 spec: `docs/superpowers/specs/2026-05-23-aws-deployment-design.md` _(후속 PR)_
> - Plan A (prereqs, manual): n/a
> - Plan B (VPC + RDS): `docs/superpowers/plans/2026-05-23-aws-deployment-plan-b-network-db.md` _(후속 PR)_
> - Plan C (EC2 + Caddy + ECR + SSM): `docs/superpowers/plans/2026-05-23-aws-deployment-plan-c-ec2-backend.md` _(후속 PR)_
> - Plan D (S3 + CloudFront + ACM + Route 53): `docs/superpowers/plans/2026-05-23-aws-deployment-plan-d-frontend-domain.md` _(후속 PR)_
> - Terraform code: `infra/terraform/` _(후속 PR)_

이미 main 에 있는 참조 문서:
- 운영 환경 변수: [`docs/OPS.md`](../OPS.md)
- 아키텍처: [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md)
