# Plan D: S3 + CloudFront + ACM + Route 53 (Phase 7-8)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** youthfit.xyz 도메인이 CloudFront(SPA) + EC2(API) 로 라우팅되도록 모든 인프라(ACM 인증서, S3 정적 호스팅, CloudFront 배포, Route 53 레코드)를 만든다. 첫 build artifact upload 와 CloudFront invalidation 은 Plan E 의 CI/CD 가 처리하므로 이 plan 에서는 다루지 않는다.

**Architecture:**
- ACM 은 CloudFront 가 `us-east-1` 인증서만 받아 들이므로 second `aws.use1` provider alias 추가
- S3 버킷은 Public access 4종 모두 차단 + CloudFront Origin Access Control (OAC) 만 `s3:GetObject` 허용
- CloudFront 는 SPA 라우팅을 위해 403/404 → `/index.html` 200 응답
- Route 53 레코드: `youthfit.xyz` / `www.youthfit.xyz` Alias → CloudFront, `api.youthfit.xyz` A → EC2 EIP
- **`n8n.youthfit.xyz` 레코드는 만들지 않는다** (n8n 미배포, [[n8n-deployment-deferred]])

**Tech Stack:** Terraform 1.7+, AWS provider 5.x, ACM, S3, CloudFront, Route 53

**Pre-flight:**
- Plan A~C 완료: EC2 EIP `13.124.202.15`, RDS, ECR, SSM 모두 떠있는 상태
- Route 53 hosted zone `Z05811777WNU2LJAW6QF` 존재 (`youthfit.xyz`, NS 가비아→AWS 위임 완료)
- Terraform state backend (`youthfit-tfstate-prod` S3 + `youthfit-tfstate-lock` DynamoDB)
- `~/.aws/credentials` 에 `youthfit-deploy` 프로파일
- 변수 `route53_zone_id` 가 이미 `variables.tf` + `terraform.tfvars` 에 설정됨 (Plan B)

**예상 소요:** 60~90분 (ACM DNS 검증 ~10분 + CloudFront 배포 ~15-20분 자동 대기 포함)

**환경변수 export (모든 task 공통):**

```bash
export AWS_PROFILE=youthfit-deploy
export AWS_REGION=ap-northeast-2
```

**예상 비용 증가**: S3 ~$0.001/월 + CloudFront ~$1-3/월 + Route 53 쿼리 ~$1/월 ≈ **월 $2-5 추가** (총 월 ~$42-45)

---

### Task 1: us-east-1 provider alias + ACM 인증서

**Files:**
- Modify: `infra/terraform/providers.tf` (append second alias provider)
- Create: `infra/terraform/acm.tf`
- Modify: `infra/terraform/outputs.tf` (append acm_certificate_arn)

> CloudFront 는 viewer cert 를 `us-east-1` 의 ACM 에서만 받아들이므로 별도 provider alias 가 필요. 그 외 리소스는 모두 `ap-northeast-2`.

- [ ] **Step 1: providers.tf 에 use1 alias 추가**

Read existing `providers.tf` first. Then append at the end:

```hcl

# CloudFront 용 ACM 인증서는 반드시 us-east-1.
provider "aws" {
  alias   = "use1"
  region  = "us-east-1"
  profile = var.aws_profile

  default_tags {
    tags = {
      Project     = "youthfit"
      Environment = "prod"
      ManagedBy   = "terraform"
    }
  }
}
```

- [ ] **Step 2: acm.tf 작성**

Create `infra/terraform/acm.tf`:

```hcl
# ──────────── ACM certificate (us-east-1, for CloudFront) ────────────
# CloudFront viewer cert 요건: us-east-1 ACM, DNS 검증.
# 와일드카드 + apex 동시 cover: youthfit.xyz + *.youthfit.xyz

resource "aws_acm_certificate" "cloudfront" {
  provider = aws.use1

  domain_name               = "youthfit.xyz"
  subject_alternative_names = ["*.youthfit.xyz"]
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "youthfit-prod-cloudfront-cert"
  }
}

# ──────────── DNS validation records (Route 53) ────────────
# ACM 가 발급 시 검증 CNAME 을 알려준다. 같은 계정의 Route 53 이라 자동 생성.

resource "aws_route53_record" "acm_validation" {
  for_each = {
    for dvo in aws_acm_certificate.cloudfront.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }

  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = var.route53_zone_id
}

# ──────────── Wait for validation ────────────
# 이 리소스가 완료되면 인증서가 `ISSUED` 상태.

resource "aws_acm_certificate_validation" "cloudfront" {
  provider = aws.use1

  certificate_arn         = aws_acm_certificate.cloudfront.arn
  validation_record_fqdns = [for record in aws_route53_record.acm_validation : record.fqdn]

  timeouts {
    create = "20m"
  }
}
```

- [ ] **Step 3: outputs.tf 에 ACM 정보 추가**

APPEND to `infra/terraform/outputs.tf`:

```hcl

output "acm_certificate_arn" {
  value       = aws_acm_certificate_validation.cloudfront.certificate_arn
  description = "us-east-1 ACM 인증서 ARN (CloudFront 용)"
}
```

- [ ] **Step 4: terraform validate**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && terraform validate
```
Expected: `Success! The configuration is valid.`

- [ ] **Step 5: terraform init -upgrade (새 provider alias 적용)**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && terraform init -upgrade
```
Expected: `Terraform has been successfully initialized!` (us-east-1 provider 가 같은 binary 라 추가 다운로드 없음)

- [ ] **Step 6: terraform plan**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && AWS_PROFILE=youthfit-deploy AWS_REGION=ap-northeast-2 terraform plan -out=acm.tfplan
```
Expected: 약 4개 리소스 추가
- `aws_acm_certificate.cloudfront`
- `aws_route53_record.acm_validation["youthfit.xyz"]`
- `aws_route53_record.acm_validation["*.youthfit.xyz"]`
- `aws_acm_certificate_validation.cloudfront`

Final: `Plan: 4 to add, 0 to change, 0 to destroy.`

- [ ] **Step 7: terraform apply (DNS 검증 ~5-15분)**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && AWS_PROFILE=youthfit-deploy AWS_REGION=ap-northeast-2 terraform apply acm.tfplan
```

Expected (마지막):
```
aws_acm_certificate.cloudfront: Creation complete after 5s
aws_route53_record.acm_validation["..."]: Creation complete
aws_acm_certificate_validation.cloudfront: Still creating... [4m elapsed]
aws_acm_certificate_validation.cloudfront: Creation complete after 4m30s

Apply complete! Resources: 4 added, 0 changed, 0 destroyed.

Outputs:

acm_certificate_arn = "arn:aws:acm:us-east-1:379197597410:certificate/..."
... (기존 outputs 유지)
```

- [ ] **Step 8: 인증서 상태 검증**

```bash
ACM_ARN=$(cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && terraform output -raw acm_certificate_arn)
AWS_PROFILE=youthfit-deploy AWS_REGION=us-east-1 aws acm describe-certificate \
  --certificate-arn "$ACM_ARN" \
  --query 'Certificate.[Status,DomainName,SubjectAlternativeNames[0]]' \
  --output table
```
Expected:
| ISSUED | youthfit.xyz | *.youthfit.xyz |

- [ ] **Step 9: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
rm -f infra/terraform/acm.tfplan
git add infra/terraform/providers.tf infra/terraform/acm.tf infra/terraform/outputs.tf
git commit -m "feat(infra): provision ACM cert for CloudFront (us-east-1)

- Second provider alias aws.use1 (us-east-1) for CloudFront-bound cert
- Wildcard cert: youthfit.xyz + *.youthfit.xyz, DNS validation via Route 53
- aws_acm_certificate_validation gates downstream resources

Plan D Task 1."
```

---

### Task 2: S3 정적 호스팅 버킷

**Files:**
- Create: `infra/terraform/s3_web.tf`
- Modify: `infra/terraform/outputs.tf` (append s3 outputs)

> 버킷 정책은 Task 3 의 CloudFront OAC 가 만들어진 뒤 Task 4 에서 붙인다. 이 task 에선 비공개 빈 버킷만.

- [ ] **Step 1: s3_web.tf 작성**

Create `infra/terraform/s3_web.tf`:

```hcl
# ──────────── S3 bucket (frontend static hosting) ────────────
# Vite build artifact 가 도착할 버킷. CloudFront OAC 만 GetObject 허용.

resource "aws_s3_bucket" "web" {
  bucket = "youthfit-web-prod"

  tags = {
    Name = "youthfit-web-prod"
  }
}

resource "aws_s3_bucket_versioning" "web" {
  bucket = aws_s3_bucket.web.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "web" {
  bucket = aws_s3_bucket.web.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "web" {
  bucket = aws_s3_bucket.web.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_ownership_controls" "web" {
  bucket = aws_s3_bucket.web.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}
```

- [ ] **Step 2: outputs.tf 에 S3 정보 추가**

APPEND to `infra/terraform/outputs.tf`:

```hcl

output "s3_web_bucket" {
  value       = aws_s3_bucket.web.id
  description = "프론트엔드 정적 자산 버킷 (Vite build sync 대상)"
}

output "s3_web_bucket_arn" {
  value = aws_s3_bucket.web.arn
}
```

- [ ] **Step 3: terraform validate + plan**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && terraform validate && \
  AWS_PROFILE=youthfit-deploy AWS_REGION=ap-northeast-2 terraform plan -out=s3.tfplan
```
Expected:
- 5 resources to add: bucket, versioning, public_access_block, SSE config, ownership controls
- `Plan: 5 to add, 0 to change, 0 to destroy.`

- [ ] **Step 4: terraform apply**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && AWS_PROFILE=youthfit-deploy AWS_REGION=ap-northeast-2 terraform apply s3.tfplan
```
Expected: `Apply complete! Resources: 5 added, 0 changed, 0 destroyed.`

- [ ] **Step 5: 버킷 상태 검증**

```bash
AWS_PROFILE=youthfit-deploy AWS_REGION=ap-northeast-2 aws s3api get-public-access-block \
  --bucket youthfit-web-prod --output table
AWS_PROFILE=youthfit-deploy AWS_REGION=ap-northeast-2 aws s3api get-bucket-versioning \
  --bucket youthfit-web-prod --output table
```
Expected:
- public access block: 4개 모두 `true`
- versioning Status: `Enabled`

- [ ] **Step 6: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
rm -f infra/terraform/s3.tfplan
git add infra/terraform/s3_web.tf infra/terraform/outputs.tf
git commit -m "feat(infra): provision S3 bucket for frontend static hosting

- youthfit-web-prod, versioning enabled, AES256 SSE
- Public access fully blocked (CloudFront OAC bucket policy in Task 4)
- BucketOwnerEnforced ownership (ACLs disabled)

Plan D Task 2."
```

---

### Task 3: CloudFront OAC + 배포

**Files:**
- Create: `infra/terraform/cloudfront.tf`
- Modify: `infra/terraform/outputs.tf` (append cloudfront outputs)

- [ ] **Step 1: cloudfront.tf 작성**

Create `infra/terraform/cloudfront.tf`:

```hcl
# ──────────── Origin Access Control ────────────
# CloudFront → S3 인증. Legacy OAI 가 아닌 OAC (SigV4) 사용.

resource "aws_cloudfront_origin_access_control" "web" {
  name                              = "youthfit-web-oac"
  description                       = "OAC for youthfit-web-prod S3 bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# ──────────── CloudFront distribution ────────────

resource "aws_cloudfront_distribution" "web" {
  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"
  comment             = "youthfit-web-prod"
  price_class         = "PriceClass_200" # NA + EU + APAC (Korea 포함). All 보다 저렴.
  http_version        = "http2and3"

  aliases = ["youthfit.xyz", "www.youthfit.xyz"]

  origin {
    domain_name              = aws_s3_bucket.web.bucket_regional_domain_name
    origin_id                = "s3-youthfit-web"
    origin_access_control_id = aws_cloudfront_origin_access_control.web.id
  }

  default_cache_behavior {
    target_origin_id       = "s3-youthfit-web"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    # AWS managed policy: CachingOptimized (기본 1d, max 1y)
    cache_policy_id = "658327ea-f89d-4fab-a63d-7e88639e58f6"

    # AWS managed policy: SecurityHeadersPolicy
    response_headers_policy_id = "67f7725c-6f97-4210-82d7-5512b31e9d03"
  }

  # SPA fallback: 403/404 (S3 가 client-side route 에 대해 returnss)
  # → /index.html 을 200 으로 반환. 단 /index.html 은 별도 캐시 정책 권장.
  custom_error_response {
    error_code         = 403
    response_code      = 200
    response_page_path = "/index.html"
    error_caching_min_ttl = 0
  }

  custom_error_response {
    error_code         = 404
    response_code      = 200
    response_page_path = "/index.html"
    error_caching_min_ttl = 0
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.cloudfront.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  tags = {
    Name = "youthfit-prod-web"
  }
}
```

- [ ] **Step 2: outputs.tf 에 CloudFront 정보 추가**

APPEND to `infra/terraform/outputs.tf`:

```hcl

output "cloudfront_distribution_id" {
  value       = aws_cloudfront_distribution.web.id
  description = "CloudFront invalidation 시 사용"
}

output "cloudfront_domain_name" {
  value       = aws_cloudfront_distribution.web.domain_name
  description = "예: d1234abcd.cloudfront.net (Route 53 Alias 의 dns_name)"
}

output "cloudfront_hosted_zone_id" {
  value       = aws_cloudfront_distribution.web.hosted_zone_id
  description = "Route 53 Alias 의 zone_id (CloudFront 전역 상수 Z2FDTNDATAQYW2)"
}
```

- [ ] **Step 3: terraform validate + plan**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && terraform validate && \
  AWS_PROFILE=youthfit-deploy AWS_REGION=ap-northeast-2 terraform plan -out=cloudfront.tfplan
```
Expected:
- 2 resources to add: OAC + distribution
- `Plan: 2 to add, 0 to change, 0 to destroy.`

- [ ] **Step 4: terraform apply (배포 ~15-20분)**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && AWS_PROFILE=youthfit-deploy AWS_REGION=ap-northeast-2 terraform apply cloudfront.tfplan
```

> CloudFront 배포는 모든 edge location 에 propagation 완료까지 15-20분 소요. Terraform 은 `Deployed` 상태가 될 때까지 대기.

Expected (마지막):
```
aws_cloudfront_distribution.web: Still creating... [18m20s elapsed]
aws_cloudfront_distribution.web: Creation complete after 18m25s

Apply complete! Resources: 2 added, 0 changed, 0 destroyed.

Outputs:

cloudfront_distribution_id = "E1234ABCDE5678"
cloudfront_domain_name = "d1234abcd.cloudfront.net"
cloudfront_hosted_zone_id = "Z2FDTNDATAQYW2"
```

- [ ] **Step 5: 배포 상태 검증**

```bash
CF_ID=$(cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && terraform output -raw cloudfront_distribution_id)
AWS_PROFILE=youthfit-deploy AWS_REGION=us-east-1 aws cloudfront get-distribution \
  --id "$CF_ID" --query 'Distribution.[Status,DistributionConfig.Enabled,DistributionConfig.Aliases.Items]' \
  --output table
```
Expected:
- Status: `Deployed`
- Enabled: `True`
- Aliases: `youthfit.xyz`, `www.youthfit.xyz`

- [ ] **Step 6: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
rm -f infra/terraform/cloudfront.tfplan
git add infra/terraform/cloudfront.tf infra/terraform/outputs.tf
git commit -m "feat(infra): provision CloudFront distribution for SPA

- Origin Access Control (SigV4) to S3 youthfit-web-prod
- Aliases: youthfit.xyz, www.youthfit.xyz
- ACM cert (us-east-1), TLS 1.2_2021 min, sni-only
- AWS managed cache (CachingOptimized) + security headers policies
- SPA fallback: 403/404 → /index.html 200
- PriceClass_200 (excludes SA + Africa)
- HTTP/2 + HTTP/3, IPv6 enabled

Plan D Task 3."
```

---

### Task 4: S3 bucket policy — CloudFront OAC GetObject

**Files:**
- Modify: `infra/terraform/s3_web.tf` (append bucket policy block)

> 이 task 는 Task 3 의 CloudFront distribution 이 존재해야만 OAC 의 source ARN 을 참조할 수 있어 분리.

- [ ] **Step 1: s3_web.tf 에 bucket policy 추가**

APPEND to `infra/terraform/s3_web.tf`:

```hcl

# ──────────── Bucket policy: CloudFront OAC GetObject 만 허용 ────────────

data "aws_iam_policy_document" "web_oac" {
  statement {
    sid     = "AllowCloudFrontOACRead"
    effect  = "Allow"
    actions = ["s3:GetObject"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    resources = ["${aws_s3_bucket.web.arn}/*"]

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.web.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "web" {
  bucket = aws_s3_bucket.web.id
  policy = data.aws_iam_policy_document.web_oac.json
}
```

- [ ] **Step 2: terraform validate + plan**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && terraform validate && \
  AWS_PROFILE=youthfit-deploy AWS_REGION=ap-northeast-2 terraform plan -out=s3_policy.tfplan
```
Expected:
- 1 resource to add: `aws_s3_bucket_policy.web`
- `Plan: 1 to add, 0 to change, 0 to destroy.`

- [ ] **Step 3: terraform apply**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && AWS_PROFILE=youthfit-deploy AWS_REGION=ap-northeast-2 terraform apply s3_policy.tfplan
```
Expected: `Apply complete! Resources: 1 added, 0 changed, 0 destroyed.`

- [ ] **Step 4: 정책 검증**

```bash
AWS_PROFILE=youthfit-deploy AWS_REGION=ap-northeast-2 aws s3api get-bucket-policy \
  --bucket youthfit-web-prod --query 'Policy' --output text | python3 -m json.tool
```
Expected: AllowCloudFrontOACRead statement 가 정확히 들어있음. Principal 이 `cloudfront.amazonaws.com` + Condition 이 CloudFront distribution ARN.

- [ ] **Step 5: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
rm -f infra/terraform/s3_policy.tfplan
git add infra/terraform/s3_web.tf
git commit -m "feat(infra): scope S3 web bucket policy to CloudFront OAC

CloudFront principal + SourceArn condition restricts s3:GetObject to
just this distribution. Public listing remains blocked at the bucket
level.

Plan D Task 4."
```

---

### Task 5: Route 53 레코드 (apex/www → CloudFront, api → EIP)

**Files:**
- Create: `infra/terraform/route53.tf`
- Modify: `infra/terraform/outputs.tf` (append dns outputs)

> **n8n.youthfit.xyz 레코드는 만들지 않는다** — n8n 미배포 ([[n8n-deployment-deferred]]). 추후 n8n 추가 시 mini-plan 에서 별도 레코드 추가.

- [ ] **Step 1: route53.tf 작성**

Create `infra/terraform/route53.tf`:

```hcl
# ──────────── Route 53 records ────────────
# apex + www → CloudFront (Alias A/AAAA)
# api → EC2 EIP (A)
# n8n → 의도적으로 미생성 (n8n 미배포)

resource "aws_route53_record" "apex_a" {
  zone_id = var.route53_zone_id
  name    = "youthfit.xyz"
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.web.domain_name
    zone_id                = aws_cloudfront_distribution.web.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "apex_aaaa" {
  zone_id = var.route53_zone_id
  name    = "youthfit.xyz"
  type    = "AAAA"

  alias {
    name                   = aws_cloudfront_distribution.web.domain_name
    zone_id                = aws_cloudfront_distribution.web.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "www_a" {
  zone_id = var.route53_zone_id
  name    = "www.youthfit.xyz"
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.web.domain_name
    zone_id                = aws_cloudfront_distribution.web.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "www_aaaa" {
  zone_id = var.route53_zone_id
  name    = "www.youthfit.xyz"
  type    = "AAAA"

  alias {
    name                   = aws_cloudfront_distribution.web.domain_name
    zone_id                = aws_cloudfront_distribution.web.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "api_a" {
  zone_id = var.route53_zone_id
  name    = "api.youthfit.xyz"
  type    = "A"
  ttl     = 300
  records = [aws_eip.web.public_ip]
}
```

- [ ] **Step 2: outputs.tf 에 도메인 정보 추가**

APPEND to `infra/terraform/outputs.tf`:

```hcl

output "frontend_url" {
  value = "https://youthfit.xyz"
}

output "frontend_www_url" {
  value = "https://www.youthfit.xyz"
}

output "api_url" {
  value = "https://api.youthfit.xyz"
}
```

- [ ] **Step 3: terraform validate + plan**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && terraform validate && \
  AWS_PROFILE=youthfit-deploy AWS_REGION=ap-northeast-2 terraform plan -out=route53.tfplan
```
Expected:
- 5 resources to add (apex A, apex AAAA, www A, www AAAA, api A)
- `Plan: 5 to add, 0 to change, 0 to destroy.`

- [ ] **Step 4: terraform apply**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && AWS_PROFILE=youthfit-deploy AWS_REGION=ap-northeast-2 terraform apply route53.tfplan
```
Expected: `Apply complete! Resources: 5 added, 0 changed, 0 destroyed.`

- [ ] **Step 5: DNS propagation 확인 (~1-5분)**

```bash
echo "=== apex (CloudFront) ==="
dig +short youthfit.xyz @8.8.8.8

echo "=== www (CloudFront) ==="
dig +short www.youthfit.xyz @8.8.8.8

echo "=== api (EIP) ==="
dig +short api.youthfit.xyz @8.8.8.8
```
Expected:
- apex/www: CloudFront IP 1~4개 (변동) — 결과가 비어있어도 잠시 후 다시
- api: `13.124.202.15`

- [ ] **Step 6: HTTPS 도달 확인 (frontend 는 S3 가 비어있어 403)**

```bash
echo "=== frontend (S3 empty → 403 expected) ==="
curl -sIk https://youthfit.xyz | head -5

echo "=== backend health endpoint ==="
curl -sk https://api.youthfit.xyz/actuator/health || echo "(backend not started yet — Plan E)"
```
Expected:
- frontend: `HTTP/2 403` (S3 가 빈 상태). 인증서 + CloudFront 라우팅이 작동하는 증거 — 이 단계에서는 정상.
- api: TLS handshake 가 일어남 (Caddy 가 Let's Encrypt 인증서 발급 가능한 상태). backend 가 안 떠있어서 502/503 또는 connection refused — 정상.

- [ ] **Step 7: Caddy 인증서 발급 트리거 (api.youthfit.xyz)**

```bash
ssh -i ~/.ssh/youthfit_prod_ed25519 -o BatchMode=yes ec2-user@13.124.202.15 \
  'sudo docker ps 2>/dev/null | grep caddy || echo "(caddy not running — Plan E)"'
```

> Caddy 는 Plan E 의 compose up 이후에 가동. 그 시점에서 `api.youthfit.xyz` 의 DNS 가 EIP 를 가리키고 있으면 Let's Encrypt HTTP-01 챌린지 자동 통과. 지금은 미리 DNS 만 셋업해두는 단계.

- [ ] **Step 8: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
rm -f infra/terraform/route53.tfplan
git add infra/terraform/route53.tf infra/terraform/outputs.tf
git commit -m "feat(infra): provision Route 53 records for apex/www/api

- youthfit.xyz, www.youthfit.xyz: A + AAAA Alias → CloudFront
- api.youthfit.xyz: A → EC2 EIP 13.124.202.15
- n8n.youthfit.xyz intentionally not created (n8n deferred)

Plan D Task 5."
```

---

### Task 6: 통합 검증 + Plan D 종료

**Files:** (없음 — 검증만)

- [ ] **Step 1: 전체 outputs 확인**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && terraform output
```
Expected (Plan A~D 누적, ~20개 outputs):
- 기존: vpc_id, subnets, sgs, web_eip, rds_*, ecr_*, ec2_*, ssh_command
- Plan D 신규: acm_certificate_arn, s3_web_bucket, cloudfront_*, frontend_url, api_url

- [ ] **Step 2: DNS propagation 종합 점검**

```bash
echo "=== apex A ==="
dig +short youthfit.xyz @8.8.8.8
dig +short youthfit.xyz @1.1.1.1
echo ""
echo "=== www A ==="
dig +short www.youthfit.xyz @8.8.8.8
echo ""
echo "=== api A (expect 13.124.202.15) ==="
dig +short api.youthfit.xyz @8.8.8.8
echo ""
echo "=== Route 53 zone records sweep ==="
AWS_PROFILE=youthfit-deploy AWS_REGION=ap-northeast-2 aws route53 list-resource-record-sets \
  --hosted-zone-id Z05811777WNU2LJAW6QF \
  --query 'ResourceRecordSets[?Type==`A` || Type==`AAAA`].[Name,Type]' \
  --output table
```
Expected:
- apex/www: CloudFront IP 들 (변동, 1~4 개)
- api: `13.124.202.15` 정확
- A/AAAA 레코드 sweep: 5행 (apex A/AAAA, www A/AAAA, api A) + 기본 NS/SOA 는 별도

- [ ] **Step 3: TLS 인증서 종단 검증**

```bash
echo "=== frontend (CloudFront ACM us-east-1) ==="
echo | openssl s_client -connect youthfit.xyz:443 -servername youthfit.xyz 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates 2>/dev/null

echo ""
echo "=== www ==="
echo | openssl s_client -connect www.youthfit.xyz:443 -servername www.youthfit.xyz 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates 2>/dev/null
```
Expected:
- subject 가 `youthfit.xyz` 또는 `*.youthfit.xyz`
- issuer 가 `Amazon`
- 만료일이 ~13개월 후

- [ ] **Step 4: HTTP → HTTPS redirect 확인 (CloudFront)**

```bash
curl -sIk http://youthfit.xyz | head -5
```
Expected:
- `HTTP/1.1 301 Moved Permanently`
- `Location: https://youthfit.xyz/`

- [ ] **Step 5: 비용 추정 갱신**

Plan D 종료 시점 시간당 비용:
- EC2 t3.small + EBS gp3 30GB: ~$0.031 (변동 없음)
- RDS db.t3.micro + gp3 20GB + 7일 백업: ~$0.024
- EIP (attached): $0
- S3 (정적 자산 빈 상태, 곧 ~50MB): ~$0.000003
- CloudFront (트래픽 < 50GB/월 가정): ~$0.0014
- Route 53 호스팅 영역 + 쿼리: ~$0.0007
- ECR storage: ~$0.0014
- ACM: $0
- SSM Standard: $0

**합계: 시간당 ~$0.058 = 일 ~$1.39 = 월 ~$42**

Plan D 추가 비용: 월 ~$2 (CloudFront + Route 53 쿼리 대부분).

---

## Plan D 완료 조건

- [ ] `aws acm describe-certificate` 가 Status `ISSUED`
- [ ] `aws s3api head-bucket --bucket youthfit-web-prod` 성공 (계정 내 접근)
- [ ] `aws cloudfront get-distribution` 가 Status `Deployed`, Enabled `True`
- [ ] `dig youthfit.xyz @8.8.8.8` 이 CloudFront IP 반환
- [ ] `dig api.youthfit.xyz @8.8.8.8` 이 `13.124.202.15` 반환
- [ ] `curl -I https://youthfit.xyz` 가 TLS handshake 통과 (S3 비어있으면 403, 인증서 검증은 OK)
- [ ] `openssl s_client -connect youthfit.xyz:443` 의 인증서 issuer 가 Amazon
- [ ] git log 에 Plan D 관련 커밋 5개 (Task 1, 2, 3, 4, 5)

## Plan D 시점의 알려진 미완료 작업

- **frontend 빌드 artifact 미업로드** — Plan E 의 GitHub Actions 가 `vite build` 결과를 `s3 sync s3://youthfit-web-prod --delete` + CloudFront invalidation `/*`
- **backend 컨테이너 미가동** — Plan E 가 ECR 이미지 push + EC2 에 deploy 자산 scp + `systemctl start youthfit`
- **Caddy TLS 인증서** — Caddy 가 처음 가동될 때 Let's Encrypt HTTP-01 으로 `api.youthfit.xyz` 자동 발급 (Plan E)
- **n8n.youthfit.xyz Route 53 레코드** — n8n 배포 시점에 별도 mini-plan
- **SES 도메인 검증** — Plan E (mail.youthfit.xyz subdomain)

## 다음 단계

**옵션 A** (권장): Plan E — SES + GitHub Actions 자동 배포. Plan C 의 Task 7/9/10 + Plan D 의 build artifact 업로드 + DB init SQL 재적용까지 한 번에.

**옵션 B**: 사용자가 로컬 n8n E2E 테스트 → 별도 Plan C-bis 로 n8n 만 prod 에 추가 ([[n8n-deployment-deferred]])

**옵션 C**: 두 옵션 병행
