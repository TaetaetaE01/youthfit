# Plan C: EC2 + Caddy + ECR + SSM + 첫 backend 가동 (Phase 4-6)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Plan B 가 만든 VPC/RDS/EIP 위에 EC2 t3.small 을 띄우고 ECR 의 backend 이미지를 docker-compose + Caddy 로 가동한다. SSM Parameter Store 에서 시크릿을 받아 `.env` 로 dump 하고, RDS 에 pgvector 확장과 초기 SQL 을 적용한다. **n8n 은 이 plan 에서 배포하지 않는다** (사용자가 로컬 E2E 테스트 후 별도 mini-plan 으로 처리).

**Architecture:**
- Terraform 으로 ECR · IAM(EC2 instance profile) · EC2 인스턴스 · EIP association · SSM 파라미터 슬롯 정의
- `docker-compose.prod.yml` 은 backend + caddy + redis 3개 서비스만 (n8n 제외)
- Caddyfile 은 `api.youthfit.xyz` 만 라우팅 (n8n.youthfit.xyz 는 Plan C-bis 에서 추가)
- 부팅 스크립트가 SSM 에서 시크릿 fetch → `/etc/youthfit/.env` 작성 → `docker compose pull && up -d`
- 첫 backend 이미지는 로컬에서 빌드 후 ECR push (이후 Plan E 에서 GitHub Actions 자동화)

**Tech Stack:** Terraform 1.7+, AWS provider 5.x, Amazon Linux 2023, Docker + compose, Caddy 2, ECR, SSM Parameter Store, PostgreSQL 17 + pgvector

**Pre-flight:**
- Plan B 완료 — VPC, public subnet, web/db SG, EIP `13.124.202.15`, RDS endpoint `youthfit-prod.cbyakqwaaevp.ap-northeast-2.rds.amazonaws.com:5432`
- SSH 키 `~/.ssh/youthfit_prod_ed25519` 존재 (Plan A 결과)
- 로컬에 Docker 가 동작 (백엔드 이미지 빌드용)
- `~/.aws/credentials` 에 `youthfit-deploy` 프로파일

**예상 소요:** 90~120분 (이미지 빌드 + push 와 EC2 부팅 대기 포함)

**환경변수 export (모든 task 공통):**

```bash
export AWS_PROFILE=youthfit-deploy
export AWS_REGION=ap-northeast-2
```

---

### Task 1: ECR repository 생성 (Terraform)

**Files:**
- Create: `infra/terraform/ecr.tf`

> n8n 의 ECR repo 는 만들지 않는다 (Plan C-bis 에서 별도 처리).

- [ ] **Step 1: ecr.tf 작성**

Create `infra/terraform/ecr.tf`:

```hcl
# ──────────── ECR repository (backend only) ────────────
# n8n image 는 Plan C 에 포함하지 않음. 로컬 E2E 테스트 후 별도 plan 에서 추가.
#
# MUTABLE 태그 유지: Plan C Task 7 에서 latest + sha-<git_sha> 두 태그를 동시 push.
# 운영 태그(latest) 가 lifecycle 로 사라지지 않도록 rule 2 는 tagPatternList 로
# 커밋 태그(sha-*, v*) 만 카운트. latest 는 보존되어 롤백 시 같은 태그로 재배포 가능.

resource "aws_ecr_repository" "backend" {
  name                 = "youthfit-backend"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Name = "youthfit-backend"
  }
}

resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after 7 days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 7
        }
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 2
        description  = "Keep last 10 commit-tagged images (latest/prod tags excluded by pattern)"
        selection = {
          tagStatus      = "tagged"
          tagPatternList = ["sha-*", "v*"]
          countType      = "imageCountMoreThan"
          countNumber    = 10
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
```

- [ ] **Step 2: outputs.tf 에 ECR URL 추가**

Modify `infra/terraform/outputs.tf` — 파일 끝에 추가:

```hcl
output "ecr_backend_url" {
  value       = aws_ecr_repository.backend.repository_url
  description = "ECR 레포 전체 URL (e.g. 379197597410.dkr.ecr.ap-northeast-2.amazonaws.com/youthfit-backend)"
}
```

- [ ] **Step 3: terraform validate**

Run:

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform
terraform validate
```

Expected: `Success! The configuration is valid.`

- [ ] **Step 4: terraform plan**

Run:

```bash
terraform plan -out=ecr.tfplan
```

Expected:
- 2개 리소스 추가 (`aws_ecr_repository.backend`, `aws_ecr_lifecycle_policy.backend`)
- 마지막: `Plan: 2 to add, 0 to change, 0 to destroy.`

- [ ] **Step 5: terraform apply**

Run:

```bash
terraform apply ecr.tfplan
```

Expected:
```
Apply complete! Resources: 2 added, 0 changed, 0 destroyed.

Outputs:

ecr_backend_url = "379197597410.dkr.ecr.ap-northeast-2.amazonaws.com/youthfit-backend"
... (기존 outputs 유지)
```

- [ ] **Step 6: ECR 검증 (CLI)**

Run:

```bash
aws ecr describe-repositories \
  --repository-names youthfit-backend \
  --query 'repositories[0].[repositoryName,repositoryUri,imageScanningConfiguration.scanOnPush]' \
  --output table
```

Expected:
| youthfit-backend | 379197597410.dkr.ecr.ap-northeast-2.amazonaws.com/youthfit-backend | True |

- [ ] **Step 7: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add infra/terraform/ecr.tf infra/terraform/outputs.tf
git commit -m "feat(infra): provision ECR repo for backend image

- youthfit-backend repo with scan-on-push
- lifecycle policy: keep last 10 images
- n8n image repo deferred (local E2E test pending)

Plan C Task 1."
```

---

### Task 2: SSM Parameter Store 슬롯 생성 (Terraform)

**Files:**
- Create: `infra/terraform/ssm.tf`

> Terraform 으로는 **슬롯과 placeholder 값**만 생성. 실제 시크릿 값은 Task 3 에서 manual `aws ssm put-parameter --overwrite` 로 채움. 이렇게 하면:
> - 슬롯 누락이 git diff 에 보임 (감사 용이)
> - 시크릿 값은 tfvars 와 무관 (state 파일에 평문 저장 안 됨)
> - `lifecycle.ignore_changes` 로 manual 갱신이 다음 apply 에서 덮어쓰지 않게 보호

> n8n 관련 슬롯 (`/youthfit/prod/n8n/*`) 은 만들지 않는다. n8n 배포 시 별도 plan 에서 추가.

- [ ] **Step 1: ssm.tf 작성**

Create `infra/terraform/ssm.tf`:

```hcl
# ──────────── SSM Parameter Store (backend secrets) ────────────
# 슬롯 정의만 Terraform 으로 관리. 실제 값은 manual put-parameter 로 채운다.
# lifecycle.ignore_changes 로 manual 갱신 보호.

locals {
  backend_secret_keys = [
    "db/password",
    "jwt/secret",
    "openai/api-key",
    "kakao/client-id",
    "kakao/client-secret",
    "internal/api-key",
  ]
}

resource "aws_ssm_parameter" "backend_secrets" {
  for_each = toset(local.backend_secret_keys)

  name        = "/youthfit/prod/${each.value}"
  type        = "SecureString"
  value       = "PLACEHOLDER_REPLACE_VIA_CLI"
  description = "Backend secret. Set via 'aws ssm put-parameter --name ${each.value} --value <real> --overwrite'."

  lifecycle {
    ignore_changes = [value]
  }

  tags = {
    Name = "youthfit-prod-${replace(each.value, "/", "-")}"
  }
}

# Non-secret 값도 같은 prefix 에 (간단 일관성). DB endpoint 는 Plan B 의 outputs 에서 가져옴.
resource "aws_ssm_parameter" "db_endpoint" {
  name        = "/youthfit/prod/db/host"
  type        = "String"
  value       = aws_db_instance.main.address
  description = "RDS Postgres host (from Plan B)."

  tags = {
    Name = "youthfit-prod-db-host"
  }
}

resource "aws_ssm_parameter" "db_name" {
  name        = "/youthfit/prod/db/name"
  type        = "String"
  value       = var.db_name
  description = "RDS database name."

  tags = {
    Name = "youthfit-prod-db-name"
  }
}

resource "aws_ssm_parameter" "db_username" {
  name        = "/youthfit/prod/db/username"
  type        = "String"
  value       = var.db_username
  description = "RDS master username."

  tags = {
    Name = "youthfit-prod-db-username"
  }
}
```

- [ ] **Step 2: terraform validate**

Run:

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform
terraform validate
```

Expected: `Success!`

- [ ] **Step 3: terraform plan**

Run:

```bash
terraform plan -out=ssm.tfplan
```

Expected:
- 9개 리소스 추가 (6 SecureString placeholders + 3 plain strings)
- `Plan: 9 to add, 0 to change, 0 to destroy.`

- [ ] **Step 4: terraform apply**

Run:

```bash
terraform apply ssm.tfplan
```

Expected: `Apply complete! Resources: 9 added, 0 changed, 0 destroyed.`

- [ ] **Step 5: 슬롯 생성 확인**

Run:

```bash
aws ssm get-parameters-by-path \
  --path /youthfit/prod \
  --recursive \
  --query 'Parameters[].[Name,Type]' \
  --output table
```

Expected: 9개 행 (db/password, db/host, db/name, db/username, jwt/secret, openai/api-key, kakao/client-id, kakao/client-secret, internal/api-key). SecureString 6개, String 3개.

- [ ] **Step 6: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add infra/terraform/ssm.tf
git commit -m "feat(infra): provision SSM Parameter Store slots for backend

- 6 SecureString placeholders (db/password, jwt/secret, openai/api-key,
  kakao/client-id, kakao/client-secret, internal/api-key)
- 3 plain String (db/host, db/name, db/username)
- lifecycle.ignore_changes protects manual updates
- n8n parameter slots deferred

Plan C Task 2."
```

---

### Task 3: SSM 슬롯에 실제 시크릿 값 채우기 (manual CLI)

**Files:** (없음 — AWS CLI 작업만)

> 이 단계 결과는 git 에 흔적 없음. SSM 콘솔 / CLI 가 권위 있는 소스.

- [ ] **Step 1: DB password 채우기**

Run (terraform.tfvars 의 db_password 값을 그대로 사용):

```bash
DB_PASSWORD=$(grep '^db_password' /Users/taetaetae/IdeaProjects/youthfit/infra/terraform/terraform.tfvars | sed -E 's/^db_password[[:space:]]*=[[:space:]]*"([^"]+)"$/\1/')
echo "DB password length: ${#DB_PASSWORD}"

aws ssm put-parameter \
  --name /youthfit/prod/db/password \
  --type SecureString \
  --value "$DB_PASSWORD" \
  --overwrite
```

Expected: `{"Version": 2, "Tier": "Standard"}` (Version 2 = placeholder 덮어쓰기 성공). Length 는 16 이상이어야 함.

- [ ] **Step 2: JWT secret 생성 및 저장**

Run:

```bash
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
echo "JWT secret length: ${#JWT_SECRET}"

aws ssm put-parameter \
  --name /youthfit/prod/jwt/secret \
  --type SecureString \
  --value "$JWT_SECRET" \
  --overwrite

unset JWT_SECRET
```

Expected: Version 2.

> 이 값은 한 번 설정 후 변경 시 모든 발급된 JWT 가 invalid. 1Password 등에 백업 보관.

- [ ] **Step 3: OpenAI API key 저장 (사용자 보유 키)**

Run (`sk-proj-...` 형태 본인 키로 치환):

```bash
read -s -p "OpenAI API key: " OPENAI_KEY
echo ""

aws ssm put-parameter \
  --name /youthfit/prod/openai/api-key \
  --type SecureString \
  --value "$OPENAI_KEY" \
  --overwrite

unset OPENAI_KEY
```

> `read -s` 는 입력을 숨김. 터미널 history 에 키가 남지 않게.

- [ ] **Step 4: Kakao OAuth 슬롯 저장**

Run:

```bash
read -p "Kakao client ID (REST API key, 공개 가능): " KAKAO_ID
read -s -p "Kakao client secret: " KAKAO_SECRET
echo ""

aws ssm put-parameter \
  --name /youthfit/prod/kakao/client-id \
  --type SecureString \
  --value "$KAKAO_ID" \
  --overwrite

aws ssm put-parameter \
  --name /youthfit/prod/kakao/client-secret \
  --type SecureString \
  --value "$KAKAO_SECRET" \
  --overwrite

unset KAKAO_ID KAKAO_SECRET
```

> Kakao client ID 는 사실 공개돼도 OK 한 값이지만, 일관성 위해 SecureString 으로 보관.

> Kakao 콘솔에서 redirect URI 에 `https://api.youthfit.xyz/auth/kakao/callback` 이 등록돼 있어야 함 — Plan D 후 등록 필요. 지금은 아직 도메인 미연결, 그래도 SSM 슬롯은 채워둠.

- [ ] **Step 5: 내부 API key 생성 (n8n → backend 호출 인증용)**

Run:

```bash
INTERNAL_KEY=$(openssl rand -base64 32 | tr -d '\n')
echo "Internal key prefix: ${INTERNAL_KEY:0:8}..."

aws ssm put-parameter \
  --name /youthfit/prod/internal/api-key \
  --type SecureString \
  --value "$INTERNAL_KEY" \
  --overwrite

# 1Password 에 저장 후
unset INTERNAL_KEY
```

> n8n 은 아직 배포 안 하지만, backend 가 부팅 시 `INTERNAL_API_KEY` env 를 요구할 수 있음. 미리 채워둠.

- [ ] **Step 6: 모든 placeholder 가 교체됐는지 확인**

Run:

```bash
aws ssm get-parameters-by-path \
  --path /youthfit/prod \
  --recursive \
  --with-decryption \
  --query 'Parameters[?Value==`PLACEHOLDER_REPLACE_VIA_CLI`].Name' \
  --output text
```

Expected: 빈 출력 (placeholder 가 남아있지 않음).

- [ ] **Step 7: Version 확인 (SecureString 들이 Version 2 이상이어야 함)**

Run:

```bash
aws ssm get-parameters-by-path \
  --path /youthfit/prod \
  --recursive \
  --query 'Parameters[].[Name,Version]' \
  --output table
```

Expected: `db/password`, `jwt/secret`, `openai/api-key`, `kakao/client-id`, `kakao/client-secret`, `internal/api-key` 모두 Version 2. plain string 3개는 Version 1.

이 Task 는 git commit 없음.

---

### Task 4: EC2 IAM role / instance profile (Terraform)

**Files:**
- Create: `infra/terraform/iam_ec2.tf`

- [ ] **Step 1: iam_ec2.tf 작성**

Create `infra/terraform/iam_ec2.tf`:

```hcl
# ──────────── EC2 IAM role + instance profile ────────────

data "aws_caller_identity" "current" {}

resource "aws_iam_role" "ec2" {
  name = "youthfit-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "ec2.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })

  tags = {
    Name = "youthfit-ec2-role"
  }
}

# ECR pull
resource "aws_iam_role_policy_attachment" "ec2_ecr" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# SSM agent + Session Manager (콘솔에서 SSH 키 없이도 접속 가능)
resource "aws_iam_role_policy_attachment" "ec2_ssm" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# SSM Parameter Store 읽기 — /youthfit/prod/* 한정
resource "aws_iam_role_policy" "ec2_ssm_params" {
  name = "ssm-parameter-read"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ssm:GetParameter",
          "ssm:GetParameters",
          "ssm:GetParametersByPath",
        ]
        Resource = "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter/youthfit/prod/*"
      },
      {
        Effect = "Allow"
        Action = [
          "kms:Decrypt",
        ]
        Resource = "arn:aws:kms:${var.aws_region}:${data.aws_caller_identity.current.account_id}:key/aws/ssm"
        # SSM SecureString 의 default KMS key (alias/aws/ssm). 명시적 별도 키 사용 시 ARN 교체.
      }
    ]
  })
}

# SES 발신 (Plan E 의 EMAIL_TRANSPORT=ses 활성 시 사용)
resource "aws_iam_role_policy" "ec2_ses" {
  name = "ses-send"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "ses:SendEmail",
        "ses:SendRawEmail",
      ]
      Resource = "*"
    }]
  })
}

# Instance profile
resource "aws_iam_instance_profile" "ec2" {
  name = "youthfit-ec2-profile"
  role = aws_iam_role.ec2.name
}
```

- [ ] **Step 2: terraform validate**

Run:

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform
terraform validate
```

Expected: `Success!`

- [ ] **Step 3: terraform plan**

Run:

```bash
terraform plan -out=iam.tfplan
```

Expected:
- 6개 리소스 추가 (role, 2 attachments, 2 inline policies, instance profile)
- `Plan: 6 to add, 0 to change, 0 to destroy.`

- [ ] **Step 4: terraform apply**

Run:

```bash
terraform apply iam.tfplan
```

Expected: `Apply complete! Resources: 6 added, 0 changed, 0 destroyed.`

- [ ] **Step 5: IAM 검증**

Run:

```bash
aws iam list-attached-role-policies --role-name youthfit-ec2-role --output table
aws iam list-role-policies --role-name youthfit-ec2-role --output table
aws iam get-instance-profile --instance-profile-name youthfit-ec2-profile \
  --query 'InstanceProfile.[InstanceProfileName,Roles[0].RoleName]' --output table
```

Expected:
- attached policies: AmazonEC2ContainerRegistryReadOnly, AmazonSSMManagedInstanceCore
- inline policies: ssm-parameter-read, ses-send
- instance profile: youthfit-ec2-profile → youthfit-ec2-role

- [ ] **Step 6: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add infra/terraform/iam_ec2.tf
git commit -m "feat(infra): provision EC2 IAM role and instance profile

- AmazonEC2ContainerRegistryReadOnly (ECR pull)
- AmazonSSMManagedInstanceCore (Session Manager + agent)
- Inline ssm-parameter-read scoped to /youthfit/prod/*
- Inline ses-send for SES

Plan C Task 4."
```

---

### Task 5: docker-compose.prod.yml + Caddyfile + 부팅 스크립트 작성

**Files:**
- Create: `deploy/docker-compose.prod.yml`
- Create: `deploy/Caddyfile`
- Create: `deploy/fetch-secrets.sh`
- Create: `deploy/youthfit.service`
- Create: `deploy/README.md`

> 이 파일들은 **EC2 위에 배치될 운영 자산**. git 에 보관하고, Task 6 의 user-data 가 부팅 시 `git clone` 으로 가져옴.

> `deploy/` 폴더에 모음 (root 직속). 백엔드 소스(`backend/`)와 분리.

- [ ] **Step 1: deploy 디렉터리 생성**

Run:

```bash
mkdir -p /Users/taetaetae/IdeaProjects/youthfit/deploy
```

- [ ] **Step 2: docker-compose.prod.yml 작성**

Create `deploy/docker-compose.prod.yml`:

```yaml
# YouthFit prod compose
#
# Includes: backend + caddy + redis
# Excluded (deferred):
#   - n8n: local E2E test pending. Add in separate plan after validation.
#   - postgres: RDS managed externally.
#
# .env file is generated at boot by /etc/youthfit/fetch-secrets.sh from SSM.

services:
  caddy:
    image: caddy:2-alpine
    container_name: youthfit-caddy
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy-data:/data
      - caddy-config:/config
    depends_on:
      - backend
    networks:
      - youthfit

  redis:
    image: redis:7-alpine
    container_name: youthfit-redis
    restart: unless-stopped
    # 외부 노출 없음. 같은 compose 네트워크 내에서만 접근.
    volumes:
      - redis-data:/data
    networks:
      - youthfit

  backend:
    image: ${ECR_BACKEND_URL}:${IMAGE_TAG:-latest}
    container_name: youthfit-backend
    restart: unless-stopped
    env_file:
      - /etc/youthfit/.env
    environment:
      SPRING_PROFILES_ACTIVE: prod
    networks:
      - youthfit
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 60s

volumes:
  caddy-data:
  caddy-config:
  redis-data:

networks:
  youthfit:
    driver: bridge
```

- [ ] **Step 3: Caddyfile 작성**

Create `deploy/Caddyfile`:

```caddy
# YouthFit Caddy config
#
# Routes:
#   api.youthfit.xyz  → backend:8080
#
# Excluded (deferred):
#   n8n.youthfit.xyz: n8n service not deployed yet. Add when n8n image is pushed to ECR.
#
# TLS: Let's Encrypt HTTP-01 challenge (port 80 inbound required in SG).

{
    # Email for Let's Encrypt notifications. Replace with admin email after first boot.
    email admin@youthfit.xyz
}

api.youthfit.xyz {
    reverse_proxy backend:8080 {
        header_up X-Real-IP {remote_host}
        header_up X-Forwarded-Proto {scheme}
    }

    encode gzip zstd

    log {
        output file /data/access.log {
            roll_size 100mb
            roll_keep 5
        }
        format json
    }

    # Health endpoint for external monitoring (not auth-required)
    handle /actuator/health {
        reverse_proxy backend:8080
    }
}

# Default — anything else gets a friendly 404. Plan D 의 frontend 는 CloudFront 가 받음.
:80 {
    respond /healthz "ok" 200
    respond * "Not Found" 404
}
```

- [ ] **Step 4: fetch-secrets.sh 작성**

Create `deploy/fetch-secrets.sh`:

```bash
#!/bin/bash
#
# Pulls /youthfit/prod/* parameters from SSM and writes /etc/youthfit/.env.
# Run by user-data at first boot, and by youthfit.service before docker compose.
#
# Requires AWS credentials from EC2 instance profile (youthfit-ec2-profile).

set -euo pipefail

ENV_FILE="/etc/youthfit/.env"
TMP_FILE="$(mktemp)"
REGION="ap-northeast-2"

# 환경변수 키 매핑: SSM path → env var name
declare -A MAPPING=(
  ["/youthfit/prod/db/host"]="DB_HOST"
  ["/youthfit/prod/db/name"]="DB_NAME"
  ["/youthfit/prod/db/username"]="DB_USERNAME"
  ["/youthfit/prod/db/password"]="DB_PASSWORD"
  ["/youthfit/prod/jwt/secret"]="JWT_SECRET"
  ["/youthfit/prod/openai/api-key"]="OPENAI_API_KEY"
  ["/youthfit/prod/kakao/client-id"]="KAKAO_CLIENT_ID"
  ["/youthfit/prod/kakao/client-secret"]="KAKAO_CLIENT_SECRET"
  ["/youthfit/prod/internal/api-key"]="INTERNAL_API_KEY"
)

echo "# Generated by fetch-secrets.sh at $(date -Iseconds)" > "$TMP_FILE"
echo "# DO NOT EDIT MANUALLY. Source of truth: SSM Parameter Store /youthfit/prod/*" >> "$TMP_FILE"
echo "" >> "$TMP_FILE"

for path in "${!MAPPING[@]}"; do
  var_name="${MAPPING[$path]}"
  value=$(aws ssm get-parameter \
    --name "$path" \
    --with-decryption \
    --region "$REGION" \
    --query 'Parameter.Value' \
    --output text)

  if [ -z "$value" ] || [ "$value" = "PLACEHOLDER_REPLACE_VIA_CLI" ]; then
    echo "ERROR: $path is empty or placeholder. Aborting." >&2
    rm -f "$TMP_FILE"
    exit 1
  fi

  echo "${var_name}=${value}" >> "$TMP_FILE"
done

# Static config (non-secret)
{
  echo ""
  echo "# Static config"
  echo "ECR_BACKEND_URL=$(aws ssm get-parameter --name /youthfit/prod/_meta/ecr-backend-url --region $REGION --query 'Parameter.Value' --output text 2>/dev/null || echo '')"
  echo "IMAGE_TAG=latest"
  echo "SPRING_PROFILES_ACTIVE=prod"
  echo "TZ=Asia/Seoul"
} >> "$TMP_FILE"

install -m 600 -o root -g root "$TMP_FILE" "$ENV_FILE"
rm -f "$TMP_FILE"

echo "Wrote $ENV_FILE ($(wc -l < $ENV_FILE) lines)"
```

> Step 4 의 `_meta/ecr-backend-url` 슬롯은 Task 6 에서 추가로 만든다 (또는 user-data 가 직접 채움). 일단 스크립트는 빈 값 허용으로 작성 — 부팅 시 env 에 비어 있으면 compose 가 실패하니, 부팅 스크립트가 명시적으로 export.

- [ ] **Step 5: youthfit.service 작성 (systemd unit)**

Create `deploy/youthfit.service`:

```ini
[Unit]
Description=YouthFit backend via docker compose
Requires=docker.service
After=docker.service network-online.target
Wants=network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/youthfit/deploy

# 1) SSM 에서 시크릿 fetch
ExecStartPre=/opt/youthfit/deploy/fetch-secrets.sh

# 2) ECR login
ExecStartPre=/bin/bash -c 'aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin $(aws sts get-caller-identity --query Account --output text).dkr.ecr.ap-northeast-2.amazonaws.com'

# 3) 이미지 pull + compose up
ExecStartPre=/usr/bin/docker compose -f docker-compose.prod.yml --env-file /etc/youthfit/.env pull
ExecStart=/usr/bin/docker compose -f docker-compose.prod.yml --env-file /etc/youthfit/.env up -d

# 정지 시 compose down (volumes 보존)
ExecStop=/usr/bin/docker compose -f docker-compose.prod.yml down

TimeoutStartSec=300

[Install]
WantedBy=multi-user.target
```

- [ ] **Step 6: deploy/README.md 작성**

Create `deploy/README.md`:

```markdown
# YouthFit 배포 자산

이 디렉터리는 prod EC2 에 배치되는 컨테이너 / 부팅 설정 파일을 담는다.

## 파일

| 파일 | 역할 |
|------|------|
| `docker-compose.prod.yml` | backend + caddy + redis 정의 |
| `Caddyfile` | TLS 종료 + api.youthfit.xyz 라우팅 |
| `fetch-secrets.sh` | SSM → `/etc/youthfit/.env` |
| `youthfit.service` | systemd unit (부팅 시 compose 자동 기동) |

## 배포 흐름

1. EC2 부팅 → cloud-init user-data 가 `git clone` 으로 이 디렉터리 동기화 (`/opt/youthfit/`)
2. `youthfit.service` enable + start
3. `fetch-secrets.sh` 가 SSM 에서 시크릿 받아 `/etc/youthfit/.env` 작성 (600 권한)
4. ECR login → `docker compose pull && up -d`
5. Caddy 가 Let's Encrypt 인증서 자동 발급
6. backend health: `curl https://api.youthfit.xyz/actuator/health`

## 보안 노트

- `/etc/youthfit/.env` 는 root:root 600. 컨테이너만 env_file 로 읽음
- Caddy 는 HTTP-01 챌린지 → 포트 80 인바운드 필수 (SG 설정 완료)
- SSM 접근은 EC2 인스턴스 프로파일(`youthfit-ec2-profile`)을 통해서만

## 제외된 컴포넌트

- **n8n**: 로컬 E2E 테스트 완료 후 별도 plan 에서 추가
  - docker-compose.prod.yml 에 service block 추가
  - Caddyfile 에 `n8n.youthfit.xyz` 라우팅 추가
  - ECR 에 `youthfit-n8n` repo 생성
  - SSM 에 `/youthfit/prod/n8n/*` 슬롯 추가
- **postgres**: RDS 매니지드 (`youthfit-prod`)
```

- [ ] **Step 7: 파일 권한 부여**

Run:

```bash
chmod +x /Users/taetaetae/IdeaProjects/youthfit/deploy/fetch-secrets.sh
ls -la /Users/taetaetae/IdeaProjects/youthfit/deploy/
```

Expected: `fetch-secrets.sh` 가 `-rwxr-xr-x`. 5개 파일 존재.

- [ ] **Step 8: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add deploy/
git commit -m "feat(deploy): add prod docker-compose, Caddyfile, boot scripts

- docker-compose.prod.yml: backend + caddy + redis (no n8n)
- Caddyfile: api.youthfit.xyz only (n8n route deferred)
- fetch-secrets.sh: SSM -> /etc/youthfit/.env
- youthfit.service: systemd unit for boot-time compose up
- deploy/README.md: deployment runbook

n8n is excluded pending local E2E validation.

Plan C Task 5."
```

---

### Task 6: EC2 인스턴스 + EIP attach (Terraform)

**Files:**
- Create: `infra/terraform/ec2.tf`
- Modify: `infra/terraform/outputs.tf` (EC2 instance ID, public DNS 추가)
- Modify: `infra/terraform/ssm.tf` (`_meta/ecr-backend-url` 슬롯 추가)

- [ ] **Step 1: ssm.tf 에 `_meta` 슬롯 추가**

Modify `infra/terraform/ssm.tf` — 파일 끝에 추가:

```hcl
# fetch-secrets.sh 가 ECR URL 을 SSM 에서 읽도록 함 (variable 의존성 줄임)
resource "aws_ssm_parameter" "meta_ecr_backend_url" {
  name        = "/youthfit/prod/_meta/ecr-backend-url"
  type        = "String"
  value       = aws_ecr_repository.backend.repository_url
  description = "ECR backend repo URL. Auto-populated."

  tags = {
    Name = "youthfit-prod-meta-ecr-backend-url"
  }
}
```

- [ ] **Step 2: ec2.tf 작성**

Create `infra/terraform/ec2.tf`:

```hcl
# ──────────── Key pair (Plan A 의 SSH 공개키) ────────────
# SSH 공개키 내용을 ssh_public_key 변수로 전달.

resource "aws_key_pair" "admin" {
  key_name   = "youthfit-prod-admin"
  public_key = var.ssh_public_key

  tags = {
    Name = "youthfit-prod-admin"
  }
}

# ──────────── AMI: Amazon Linux 2023 최신 ────────────

data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# ──────────── User data (cloud-init) ────────────

locals {
  # user-data 는 EC2 의 시스템 환경(Docker + 디렉터리) 만 준비한다.
  # deploy/ 자산(docker-compose.prod.yml, Caddyfile, fetch-secrets.sh,
  # youthfit.service)과 컨테이너 이미지 push 는 Plan E 의 GitHub Actions
  # 가 SSH/scp 로 EC2 에 전달하는 표준 CI/CD 패턴을 사용한다.
  user_data = <<-USERDATA
    #!/bin/bash
    set -euxo pipefail
    exec > >(tee /var/log/youthfit-bootstrap.log) 2>&1

    # 1. 시스템 업데이트
    dnf update -y
    dnf install -y docker git jq amazon-cloudwatch-agent

    # 2. Docker
    systemctl enable --now docker
    usermod -aG docker ec2-user

    # 3. Docker Compose plugin (al2023 의 docker 패키지는 compose plugin 별도)
    DOCKER_CONFIG=$${DOCKER_CONFIG:-/usr/local/lib/docker}
    mkdir -p $DOCKER_CONFIG/cli-plugins
    curl -SL https://github.com/docker/compose/releases/download/v2.27.0/docker-compose-linux-x86_64 \
      -o $DOCKER_CONFIG/cli-plugins/docker-compose
    chmod +x $DOCKER_CONFIG/cli-plugins/docker-compose

    # 4. youthfit 디렉터리 (deploy 자산은 Plan E 의 CI/CD 가 scp 로 도착시킴)
    mkdir -p /opt/youthfit
    mkdir -p /etc/youthfit
    chmod 700 /etc/youthfit
    chown ec2-user:ec2-user /opt/youthfit

    echo "Bootstrap complete. Docker ready. deploy/ assets pending CI delivery."
  USERDATA
}

# ──────────── EC2 instance ────────────

resource "aws_instance" "web" {
  ami                    = data.aws_ami.al2023.id
  instance_type          = "t3.small"
  key_name               = aws_key_pair.admin.key_name
  iam_instance_profile   = aws_iam_instance_profile.ec2.name
  vpc_security_group_ids = [aws_security_group.web.id]
  subnet_id              = aws_subnet.public["0"].id

  # 퍼블릭 서브넷이지만 map_public_ip_on_launch=false 라 EIP 로만 접근.
  associate_public_ip_address = false

  user_data                   = local.user_data
  user_data_replace_on_change = false

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 30
    encrypted             = true
    delete_on_termination = true

    tags = {
      Name = "youthfit-prod-web-root"
    }
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required" # IMDSv2 only
    http_put_response_hop_limit = 2
  }

  tags = {
    Name = "youthfit-prod-web"
  }

  lifecycle {
    ignore_changes = [
      ami,        # AMI 갱신은 explicit 작업으로
      user_data,  # user-data 변경은 새 인스턴스 만들 때만 (Plan F)
    ]
  }
}

# ──────────── EIP attach ────────────

resource "aws_eip_association" "web" {
  instance_id   = aws_instance.web.id
  allocation_id = aws_eip.web.id
}
```

- [ ] **Step 3: variables.tf 에 SSH 공개키 / GitHub repo 변수 추가**

Modify `infra/terraform/variables.tf` — 파일 끝에 추가:

```hcl
variable "ssh_public_key" {
  type        = string
  description = "SSH ed25519 public key contents (one line). cat ~/.ssh/youthfit_prod_ed25519.pub"
  sensitive   = false
}

variable "github_owner" {
  type        = string
  default     = "TaetaetaE01"
  description = "GitHub user/org owning the youthfit repo (for user-data git clone)."
}

variable "github_repo" {
  type        = string
  default     = "youthfit"
  description = "GitHub repo name. Public read access required for user-data clone."
}
```

- [ ] **Step 4: terraform.tfvars 에 SSH 공개키 값 추가**

Run (`~/.ssh/youthfit_prod_ed25519.pub` 내용을 그대로 가져옴):

```bash
SSH_PUB=$(cat ~/.ssh/youthfit_prod_ed25519.pub)
echo "Public key: $SSH_PUB"
```

Edit `infra/terraform/terraform.tfvars` — 파일 끝에 추가 (한 줄로):

```hcl
ssh_public_key = "ssh-ed25519 AAAA... user@host"
```

> Step 4 의 출력을 그대로 따옴표 안에 붙여넣기. 줄바꿈 없어야 함.

- [ ] **Step 5: outputs.tf 에 EC2 outputs 추가**

Modify `infra/terraform/outputs.tf` — 파일 끝에 추가:

```hcl
output "ec2_instance_id" {
  value = aws_instance.web.id
}

output "ec2_private_ip" {
  value = aws_instance.web.private_ip
}

output "ec2_public_ip" {
  value       = aws_eip.web.public_ip
  description = "EIP attached to EC2. Same as web_eip but explicit alias."
}

output "ssh_command" {
  value       = "ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@${aws_eip.web.public_ip}"
  description = "SSH 명령 (EIP 가 attach 된 후 사용)"
}
```

- [ ] **Step 6: terraform validate**

Run:

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform
terraform validate
```

Expected: `Success!`

- [ ] **Step 7: terraform plan**

Run:

```bash
terraform plan -out=ec2.tfplan
```

Expected:
- 4개 리소스 추가 (key_pair, instance, EIP association, SSM `_meta/ecr-backend-url`)
- `Plan: 4 to add, 0 to change, 0 to destroy.`
- AMI 가 `data.aws_ami.al2023` 로 알아서 결정되므로 ID 값이 plan 에 표시됨

- [ ] **Step 8: terraform apply (EC2 부팅 ~2분, user-data ~3-5분)**

Run:

```bash
terraform apply ec2.tfplan
```

Expected:
```
aws_key_pair.admin: Creation complete after 1s
aws_ssm_parameter.meta_ecr_backend_url: Creation complete after 1s
aws_instance.web: Creating...
aws_instance.web: Still creating... [20s elapsed]
...
aws_instance.web: Creation complete after 1m45s
aws_eip_association.web: Creation complete after 5s

Apply complete! Resources: 4 added, 0 changed, 0 destroyed.

Outputs:

ec2_instance_id = "i-0..."
ec2_public_ip   = "13.124.202.15"
ssh_command     = "ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@13.124.202.15"
```

- [ ] **Step 9: EIP 가 attached 됐는지 확인**

Run:

```bash
aws ec2 describe-addresses --public-ips 13.124.202.15 \
  --query 'Addresses[0].[PublicIp,InstanceId,AssociationId]' --output table
```

Expected: InstanceId 가 채워져 있고 AssociationId 도 채워짐.

- [ ] **Step 10: user-data 진행 상황 확인 (SSH 가능까지 대기)**

EC2 에 cloud-init 이 끝나려면 보통 3-5분 추가 소요. SSH 가능 여부 확인:

```bash
for i in {1..30}; do
  if ssh -i ~/.ssh/youthfit_prod_ed25519 -o ConnectTimeout=5 -o StrictHostKeyChecking=accept-new ec2-user@13.124.202.15 'echo ready' 2>/dev/null; then
    echo "SSH ready"
    break
  fi
  echo "Waiting... ($i/30)"
  sleep 10
done
```

Expected: 결국 `ready` 출력.

> 안 되면 SG (22 port from admin IP) 확인. 본인 IP 가 바뀌었으면 `terraform.tfvars` 의 `admin_ssh_cidr` 갱신 후 `terraform apply`.

- [ ] **Step 11: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add infra/terraform/ec2.tf infra/terraform/ssm.tf infra/terraform/variables.tf infra/terraform/outputs.tf
git commit -m "feat(infra): provision EC2 t3.small + EIP association

- Amazon Linux 2023, gp3 30GB encrypted root
- IMDSv2 required
- cloud-init installs docker + compose plugin + git
- Clones /opt/youthfit and installs systemd unit (not started)
- EIP 13.124.202.15 attached
- SSM _meta/ecr-backend-url added for boot script
- variables: ssh_public_key, github_owner, github_repo

Plan C Task 6."
```

---

### Task 7: 로컬에서 backend 이미지 빌드 + ECR push

**Files:** (없음 — 로컬 docker build 만)

- [ ] **Step 1: 백엔드 Dockerfile 존재 확인**

Run:

```bash
ls -la /Users/taetaetae/IdeaProjects/youthfit/backend/Dockerfile
```

Expected: 파일 존재.

만약 없다면 STOP — Dockerfile 작성이 별도 task 가 필요함. 일반 Spring Boot Dockerfile 패턴:

```dockerfile
# /Users/taetaetae/IdeaProjects/youthfit/backend/Dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 2: 로컬에서 gradle build**

Run:

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/backend
./gradlew clean bootJar
ls -la build/libs/
```

Expected: `youthfit-backend-X.Y.Z.jar` (또는 비슷한 이름) 생성.

- [ ] **Step 3: docker build**

Run:

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/backend

GIT_SHA=$(git -C /Users/taetaetae/IdeaProjects/youthfit rev-parse --short HEAD)
echo "Building image with SHA tag: $GIT_SHA"

docker buildx build \
  --platform linux/amd64 \
  -t youthfit-backend:latest \
  -t youthfit-backend:$GIT_SHA \
  .
```

> `--platform linux/amd64` 는 macOS Apple Silicon 에서 빌드 시 필수 (EC2 가 amd64).

Expected: `Successfully tagged youthfit-backend:latest` 비슷한 메시지.

- [ ] **Step 4: ECR 로그인**

Run:

```bash
ECR_URL=$(cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && terraform output -raw ecr_backend_url)
ECR_REGISTRY="${ECR_URL%/*}"

aws ecr get-login-password --region ap-northeast-2 | \
  docker login --username AWS --password-stdin "$ECR_REGISTRY"
```

Expected: `Login Succeeded`.

- [ ] **Step 5: 태그 + push**

Run:

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/backend

GIT_SHA=$(git -C /Users/taetaetae/IdeaProjects/youthfit rev-parse --short HEAD)
ECR_URL=$(cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && terraform output -raw ecr_backend_url)

docker tag youthfit-backend:latest "$ECR_URL:latest"
docker tag youthfit-backend:$GIT_SHA "$ECR_URL:sha-$GIT_SHA"

docker push "$ECR_URL:latest"
docker push "$ECR_URL:sha-$GIT_SHA"
```

Expected: 두 태그 모두 push 완료. 각 push 마지막에 `digest: sha256:...` 출력.

- [ ] **Step 6: ECR 에 이미지 도착 확인**

Run:

```bash
aws ecr describe-images \
  --repository-name youthfit-backend \
  --query 'imageDetails[].[imageTags[0],imagePushedAt,imageSizeInBytes]' \
  --output table
```

Expected: 2행 이상 (latest + sha 태그). imagePushedAt 이 방금 시각.

이 Task 는 git commit 없음 (이미지만 ECR 로).

---

### Task 8: RDS 에 pgvector 활성 + 초기 SQL 적용

**Files:** (없음 — RDS 에 직접 SQL 적용)

> RDS 는 private subnet 에 있어 EC2 (web SG) 에서만 접근 가능. EC2 에 SSH 로 들어가서 psql 로 작업.

- [ ] **Step 1: EC2 에 psql 설치**

Run:

```bash
ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@13.124.202.15 \
  'sudo dnf install -y postgresql17 && psql --version'
```

Expected: `psql (PostgreSQL) 17.x` 출력.

- [ ] **Step 2: RDS 접속 정보를 EC2 환경변수로 전달**

Run (로컬에서):

```bash
RDS_HOST=$(cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && terraform output -raw rds_address)
DB_PASSWORD=$(grep '^db_password' /Users/taetaetae/IdeaProjects/youthfit/infra/terraform/terraform.tfvars | sed -E 's/^db_password[[:space:]]*=[[:space:]]*"([^"]+)"$/\1/')

echo "RDS host: $RDS_HOST"
echo "DB password length: ${#DB_PASSWORD}"

ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@13.124.202.15 \
  "PGPASSWORD='$DB_PASSWORD' psql -h $RDS_HOST -U youthfit -d youthfit -c 'SELECT version();'"
```

Expected: `PostgreSQL 17.x on x86_64-pc-linux-gnu, ...` 출력. 연결 성공.

> 실패 시 `Operation timed out` → DB SG ingress 확인. `connection refused` → RDS DBInstanceStatus available 확인.

- [ ] **Step 3: pgvector 확장 활성화**

Run:

```bash
RDS_HOST=$(cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && terraform output -raw rds_address)
DB_PASSWORD=$(grep '^db_password' /Users/taetaetae/IdeaProjects/youthfit/infra/terraform/terraform.tfvars | sed -E 's/^db_password[[:space:]]*=[[:space:]]*"([^"]+)"$/\1/')

ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@13.124.202.15 \
  "PGPASSWORD='$DB_PASSWORD' psql -h $RDS_HOST -U youthfit -d youthfit -c 'CREATE EXTENSION IF NOT EXISTS vector;'"
```

Expected: `CREATE EXTENSION`.

- [ ] **Step 4: 확장 등록 확인**

Run:

```bash
RDS_HOST=$(cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && terraform output -raw rds_address)
DB_PASSWORD=$(grep '^db_password' /Users/taetaetae/IdeaProjects/youthfit/infra/terraform/terraform.tfvars | sed -E 's/^db_password[[:space:]]*=[[:space:]]*"([^"]+)"$/\1/')

ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@13.124.202.15 \
  "PGPASSWORD='$DB_PASSWORD' psql -h $RDS_HOST -U youthfit -d youthfit -c \"SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';\""
```

Expected:
```
 extname | extversion
---------+-----------
 vector  | 0.7.x
```

- [ ] **Step 5: 기존 init SQL 파일 목록 확인**

Run (로컬에서):

```bash
ls /Users/taetaetae/IdeaProjects/youthfit/db/init/*.sql 2>/dev/null || echo "no db/init dir"
ls /Users/taetaetae/IdeaProjects/youthfit/backend/src/main/resources/sql/*.sql 2>/dev/null || echo "no backend/sql dir"
```

> 파일이 없으면 Step 6, 7 은 skip. backend 가 부팅 시 `ddl-auto=update` 또는 별도 마이그레이션을 수행한다면 init SQL 불필요.

- [ ] **Step 6: init SQL 을 EC2 로 복사**

Run (init SQL 파일이 있을 경우):

```bash
scp -i ~/.ssh/youthfit_prod_ed25519 \
  /Users/taetaetae/IdeaProjects/youthfit/db/init/*.sql \
  ec2-user@13.124.202.15:/tmp/

scp -i ~/.ssh/youthfit_prod_ed25519 \
  /Users/taetaetae/IdeaProjects/youthfit/backend/src/main/resources/sql/*.sql \
  ec2-user@13.124.202.15:/tmp/ 2>/dev/null || true
```

> ⚠️ **부분 적용 가능성**: YouthFit 의 init SQL 중 다수는 `policy`, `users`, `eligibility_rule`, `notification_setting`, `policy_document` 등의 **base table 위에 ALTER / CREATE INDEX / COMMENT 를 추가하는 마이그레이션**이다. base table 들은 `Flyway 미사용` 정책에 따라 JPA Hibernate 의 ddl-auto 가 backend 첫 부팅 시 만든다. 즉 backend 부팅 **전에** init SQL 을 적용하면 base table 미존재로 절반 정도가 fail 한다. 이 상태도 정상 — `ON_ERROR_STOP=1` 없이 progress 하면 self-contained SQL (예: `email_send_attempt`, `ingestion_run_log`, `qna_question_cache` 등 신규 테이블 생성과 pgvector 사용 인덱스) 은 적용되고, ALTER 의존 SQL 은 skip 된다. backend 가 한 번 부팅한 뒤 (Plan E) 같은 SQL 들을 다시 실행하면 idempotent / IF NOT EXISTS 로 잘 정리된다.

- [ ] **Step 7: 순서대로 적용 (`ON_ERROR_STOP=0` — base-table 의존은 skip 되도록)**

Run:

```bash
RDS_HOST=$(cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && terraform output -raw rds_address)
DB_PASSWORD=$(grep '^db_password' /Users/taetaetae/IdeaProjects/youthfit/infra/terraform/terraform.tfvars | sed -E 's/^db_password[[:space:]]*=[[:space:]]*"([^"]+)"$/\1/')

ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@13.124.202.15 << EOF
set -e
for f in \$(ls /tmp/*.sql | sort); do
  echo "=== Applying \$f ==="
  PGPASSWORD='$DB_PASSWORD' psql -h $RDS_HOST -U youthfit -d youthfit -f "\$f"
done
echo "All SQL files applied."
EOF
```

Expected: 각 파일이 차례로 적용. 마지막 `All SQL files applied.` 출력.

- [ ] **Step 8: 테이블 생성 확인**

Run:

```bash
RDS_HOST=$(cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform && terraform output -raw rds_address)
DB_PASSWORD=$(grep '^db_password' /Users/taetaetae/IdeaProjects/youthfit/infra/terraform/terraform.tfvars | sed -E 's/^db_password[[:space:]]*=[[:space:]]*"([^"]+)"$/\1/')

ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@13.124.202.15 \
  "PGPASSWORD='$DB_PASSWORD' psql -h $RDS_HOST -U youthfit -d youthfit -c '\dt'"
```

Expected: backend 가 사용하는 테이블 목록 (없으면 backend 의 ddl-auto 또는 마이그레이션이 처리할 것).

이 Task 는 git commit 없음 (DB 스키마만 적용).

---

### Task 9: EC2 에서 docker compose up + health check

**Files:** (없음 — EC2 운영)

> Plan C 의 user-data 는 docker 환경만 준비. deploy/ 자산은 이 Task 의 Step 0 에서 manual scp 로 보낸다. Plan E (GitHub Actions) 부터는 동일 작업이 자동화된다.

- [ ] **Step 0: deploy/ 자산을 EC2 로 전송 (manual scp)**

Run (로컬에서):

```bash
scp -i ~/.ssh/youthfit_prod_ed25519 -r \
  /Users/taetaetae/IdeaProjects/youthfit/deploy \
  ec2-user@13.124.202.15:/opt/youthfit/

ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@13.124.202.15 \
  'chmod +x /opt/youthfit/deploy/fetch-secrets.sh && sudo cp /opt/youthfit/deploy/youthfit.service /etc/systemd/system/youthfit.service && sudo systemctl daemon-reload && sudo systemctl enable youthfit.service'
```

Expected: scp 성공, ssh 명령에서 권한 / systemd reload / enable 성공.

- [ ] **Step 1: user-data 가 잘 완료됐는지 확인**

Run:

```bash
ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@13.124.202.15 \
  'sudo tail -30 /var/log/youthfit-bootstrap.log'
```

Expected: 마지막 줄 `Bootstrap complete. Backend image push and 'systemctl start youthfit' required.`

만약 에러가 보이면 해당 line 의 명령을 manual 로 재실행하고 systemd unit 위치도 확인:

```bash
ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@13.124.202.15 \
  'ls -la /etc/systemd/system/youthfit.service /opt/youthfit/deploy/'
```

- [ ] **Step 2: fetch-secrets.sh 단독 실행 (dry-run)**

Run:

```bash
ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@13.124.202.15 \
  'sudo /opt/youthfit/deploy/fetch-secrets.sh && sudo head -5 /etc/youthfit/.env'
```

Expected:
```
Wrote /etc/youthfit/.env (XX lines)
# Generated by fetch-secrets.sh at 2026-MM-DDTHH:MM:SS+09:00
# DO NOT EDIT MANUALLY. Source of truth: SSM Parameter Store /youthfit/prod/*

DB_HOST=youthfit-prod....rds.amazonaws.com
```

> head 5 에 평문이 보이는 것은 의도된 동작 (root 권한으로 read). 일반 사용자는 600 권한 때문에 읽을 수 없음.

- [ ] **Step 3: 권한 확인**

Run:

```bash
ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@13.124.202.15 \
  'ls -la /etc/youthfit/.env && cat /etc/youthfit/.env 2>&1 | head -2'
```

Expected:
- `-rw------- 1 root root ...`
- `cat: /etc/youthfit/.env: Permission denied`

(ec2-user 가 직접 read 불가 = 안전)

- [ ] **Step 4: systemd unit start**

Run:

```bash
ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@13.124.202.15 \
  'sudo systemctl start youthfit.service && sudo systemctl status youthfit.service --no-pager'
```

Expected: `Active: active (exited)` (oneshot type 이라 exited 가 정상).

ExecStartPre 단계들이 모두 통과해야 함:
1. fetch-secrets.sh → OK
2. ECR login → OK
3. docker compose pull → OK (이미지 푸시 완료된 상태)
4. docker compose up -d → OK

- [ ] **Step 5: 컨테이너 동작 확인**

Run:

```bash
ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@13.124.202.15 \
  'sudo docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"'
```

Expected: 3개 컨테이너 모두 `Up` 상태.
- `youthfit-caddy` (0.0.0.0:80, 0.0.0.0:443)
- `youthfit-redis` (internal)
- `youthfit-backend` (internal :8080)

n8n 은 없어야 함.

- [ ] **Step 6: backend health check (인스턴스 내부)**

Run:

```bash
ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@13.124.202.15 \
  'sudo docker exec youthfit-backend curl -sf http://localhost:8080/actuator/health'
```

Expected: `{"status":"UP",...}` JSON.

- [ ] **Step 7: Caddy 가 HTTP 80 에 응답하는지 확인 (인스턴스 외부, 도메인 없이)**

Run (로컬에서):

```bash
curl -v http://13.124.202.15/healthz
```

Expected: `HTTP/1.1 200 OK` + body `ok`.

> HTTPS 로는 아직 도달 불가 (Caddy 가 Let's Encrypt 인증서 발급을 위해 도메인이 EIP 를 가리켜야 하는데, Route 53 레코드가 Plan D 에서 등록됨). 현재는 HTTP 만.

- [ ] **Step 8: Caddy 로그 확인 (LE 발급 시도 흔적)**

Run:

```bash
ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@13.124.202.15 \
  'sudo docker logs youthfit-caddy 2>&1 | tail -30'
```

Expected: TLS 발급 실패 메시지가 보일 수 있음 (도메인 DNS 미연결). 정상.

```
{"level":"error","msg":"could not get certificate from issuer",...}
```

이 에러는 Plan D 가 끝나면 자동 해소 (Caddy 가 백그라운드에서 재시도).

- [ ] **Step 9: backend 로그 점검**

Run:

```bash
ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@13.124.202.15 \
  'sudo docker logs youthfit-backend 2>&1 | tail -50'
```

Expected:
- `Started YouthFitApplication in X.XXX seconds` 또는 비슷한 Spring Boot 부팅 로그
- DB connection 성공 (HikariCP 메시지)
- 에러 없음

DB 연결 실패 시 (가장 흔한 문제): `.env` 의 `DB_HOST` / `DB_PASSWORD` 확인 + DB SG 확인.

이 Task 는 git commit 없음 (운영 작업).

---

### Task 10: 결과 검증 + Plan C 종료 체크리스트

**Files:** (없음 — 검증만)

- [ ] **Step 1: 전체 outputs 확인**

Run:

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform
terraform output
```

Expected: Plan B 의 9개 + Plan C 의 5개 = 14개 outputs. 특히:
- `ecr_backend_url`
- `ec2_instance_id`
- `ec2_public_ip` = "13.124.202.15"
- `ssh_command`

- [ ] **Step 2: EC2 상태 점검 sweep**

Run:

```bash
echo "=== EC2 instance ==="
aws ec2 describe-instances \
  --filters "Name=tag:Name,Values=youthfit-prod-web" \
  --query 'Reservations[0].Instances[0].[InstanceId,State.Name,InstanceType,PublicIpAddress]' \
  --output table

echo ""
echo "=== ECR images ==="
aws ecr describe-images --repository-name youthfit-backend \
  --query 'imageDetails[].[imageTags[0],imagePushedAt]' --output table

echo ""
echo "=== SSM params ==="
aws ssm get-parameters-by-path --path /youthfit/prod --recursive \
  --query 'Parameters[].Name' --output text | tr '\t' '\n' | sort
```

Expected:
- Instance: running, t3.small, 13.124.202.15
- ECR: 2+ 이미지 태그
- SSM: 10개 슬롯 (6 SecureString + 3 plain + `_meta/ecr-backend-url`), n8n 관련 없음

- [ ] **Step 3: 외부 HTTP health 호출 (도메인 없이)**

Run:

```bash
curl -sf http://13.124.202.15/healthz
echo ""

# backend health 는 Caddy 의 api.youthfit.xyz 라우팅 통해서만 접근 → 지금은 도메인 미연결
# Plan D 후 다시 검증
```

Expected: `ok`.

- [ ] **Step 4: systemd unit enabled 확인 (재부팅 시 자동 기동)**

Run:

```bash
ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@13.124.202.15 \
  'sudo systemctl is-enabled youthfit.service'
```

Expected: `enabled`.

- [ ] **Step 5: 비용 추정 갱신**

Plan C 종료 시점 시간당 비용:
- EC2 t3.small: ~$0.026/시간 (월 ~$19)
- EBS gp3 30GB: ~$0.005/시간 (월 ~$3)
- RDS db.t3.micro: ~$0.020/시간 (월 ~$14)
- RDS gp3 20GB: ~$0.004/시간 (월 ~$3)
- EIP (attached): $0
- ECR storage (1-2GB): ~$0.001/시간 (월 ~$0.20)
- SSM Standard tier: $0
- S3 state + DynamoDB: ~$0

**합계: 시간당 ~$0.056 = 일 ~$1.34 = 월 ~$40**

Plan D, E 가 추가하는 항목 (CloudFront, ACM, SES, Route 53 쿼리) 은 합쳐도 월 $5-10 추가 예상.

---

## Plan C 완료 조건

- [ ] `terraform apply` 누적 성공, state 에 EC2 + EIP association + ECR + IAM + SSM 모두 반영
- [ ] `aws ecr describe-images --repository-name youthfit-backend` 가 최소 1개 이미지 반환
- [ ] `aws ssm get-parameters-by-path --path /youthfit/prod` 가 10개 슬롯 반환 (n8n 없음)
- [ ] `aws ec2 describe-instances --instance-ids <id>` 가 `running` 상태
- [ ] EC2 SSH 접속 가능 (`ssh youthfit-prod` 또는 `ssh -i ~/.ssh/youthfit_prod_ed25519 ec2-user@13.124.202.15`)
- [ ] EC2 에서 `sudo systemctl is-active youthfit` = `active`
- [ ] `docker ps` 가 3개 컨테이너 (backend, caddy, redis) 모두 Up
- [ ] backend `/actuator/health` 가 `UP`
- [ ] RDS 에 `vector` 확장 등록
- [ ] git log 에 Plan C 관련 커밋 5개 (Task 1, 2, 4, 5, 6)
- [ ] `deploy/` 디렉터리에 4개 파일 (compose, Caddyfile, fetch-secrets.sh, youthfit.service, README)

## Plan C 시점의 알려진 미완료 작업

- **n8n 미배포** — 사용자의 로컬 E2E 테스트 후 별도 plan (Plan C-bis) 으로 처리:
  - `deploy/docker-compose.prod.yml` 에 n8n service 추가
  - `deploy/Caddyfile` 에 `n8n.youthfit.xyz` 라우팅 추가
  - ECR `youthfit-n8n` repo 생성 (또는 공식 `n8nio/n8n` 이미지 직접 pull)
  - SSM `/youthfit/prod/n8n/*` 슬롯 (basic-auth password, encryption key 등) 추가
- 도메인 미연결 — `api.youthfit.xyz` DNS 레코드 (Plan D)
- ACM 인증서 미발급 (CloudFront 용) — Plan D
- S3 + CloudFront 미생성 (프론트엔드) — Plan D
- SES 도메인 검증 미진행 — Plan E
- GitHub Actions 자동 배포 미설정 (수동 push 만 가능) — Plan E
- IAM 권한 좁히기 (AdministratorAccess → 최소 권한) — Plan F
- CloudWatch 알람 미설정 — Plan F

## 다음 단계

**옵션 A**: Plan D 작성 — S3 + CloudFront + ACM + Route 53 레코드 등록
**옵션 B**: 사용자가 로컬 n8n E2E 테스트 진행 → 완료 후 Plan C-bis 로 n8n 만 prod 에 추가
**옵션 C**: 두 옵션 병행 (Plan D 진행 + 사용자 로컬에서 n8n 검증)

권장: **옵션 C**. Plan D 는 프론트엔드 트래픽 받기 시작, n8n 은 백오피스용이라 직교 작업.

---

## 실행 후기 (2026-05-26 종료 시점)

**완료**: Task 1~6 + Task 8 부분. 인프라 (ECR · SSM · IAM · EC2 · EIP · pgvector · self-contained init SQL) 까지 모두 prod 에 떠 있는 상태.

**Plan E 로 미룬 항목** (사용자 의도: "클라우드만 띄우고 컨테이너는 GitHub CI/CD 로"):
- Task 7 (로컬 docker build + ECR push) → GitHub Actions build job 으로 통합
- Task 9 (deploy 자산 scp + systemctl start) → GitHub Actions deploy job 으로 통합
- Task 10 (최종 검증) → Plan E end-to-end smoke 로 통합
- Task 8 의 base-table 의존 init SQL 재적용 → Plan E 의 backend 첫 부팅 직후 step 으로 통합 ([[db-schema-init-order]])

**user-data 단순화 결정**: EC2 가 GitHub repo 를 직접 clone 하는 패턴은 private repo 인증 / 갱신 복잡도가 커져서, docker 환경 준비까지만 cloud-init 으로 처리. deploy 자산 전달은 CI/CD 표준 패턴 (scp/rsync from runner) 으로.
