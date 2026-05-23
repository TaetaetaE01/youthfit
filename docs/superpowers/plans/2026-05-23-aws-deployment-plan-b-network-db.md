# Plan B: Terraform 기반 + 네트워크 + RDS (Phase 1-3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Terraform state 백엔드(S3+DynamoDB) 부트스트랩 후, 정식 Terraform 프로젝트로 VPC·서브넷·SG·EIP·RDS Postgres(pgvector) 를 생성한다. Plan B 종료 시점에 `terraform output rds_address` 로 RDS 엔드포인트를 얻고 같은 VPC 안에서 도달 가능한 상태가 된다.

**Architecture:** `infra/terraform/` 디렉터리에 정식 Terraform 모듈. 백엔드 state 는 `youthfit-tfstate-prod` S3 버킷 + `youthfit-tfstate-lock` DynamoDB 테이블에 저장. 첫 적용은 manual AWS CLI 로 부트스트랩, 이후 모든 인프라는 Terraform 으로 관리.

**Tech Stack:** Terraform 1.7+, AWS provider 5.x, AWS CLI v2, PostgreSQL 17 + pgvector

**Pre-flight:**
- Plan A 완료 (IAM 사용자·MFA·SSH 키·Route 53 zone ID 확보)
- Terraform 설치 (`brew install terraform` 또는 tfenv) — 확인: `terraform version` ≥ 1.7
- `jq` 설치 (검증 명령에서 사용) — 확인: `jq --version`
- 본인의 공인 IP 확인 가능 (SSH 화이트리스트용)

**예상 소요:** 60~90분 (RDS 생성 자체에 10분 소요)

---

### Task 1: Terraform state 백엔드 부트스트랩 (S3 + DynamoDB)

**Files:** (AWS CLI 작업만, 로컬 파일 변경 없음)

> Terraform 의 backend "s3" 는 S3 버킷이 미리 존재해야 작동. 이걸 또 Terraform 으로 만들면 chicken-and-egg 문제 발생 → AWS CLI 로 한 번만 수동 생성.

- [ ] **Step 1: AWS_PROFILE 환경변수 설정**

Run:

```bash
export AWS_PROFILE=youthfit-deploy
export AWS_REGION=ap-northeast-2
```

> 이후 모든 명령에서 이 두 환경변수를 사용. 새 터미널 세션마다 다시 export.

- [ ] **Step 2: state 용 S3 버킷 생성**

Run:

```bash
aws s3api create-bucket \
  --bucket youthfit-tfstate-prod \
  --region ap-northeast-2 \
  --create-bucket-configuration LocationConstraint=ap-northeast-2
```

Expected output: JSON 에 `"Location": "http://youthfit-tfstate-prod.s3.amazonaws.com/"` 비슷한 형태.

> 버킷 이름 충돌(BucketAlreadyExists)이 나면 suffix 변경: 예 `youthfit-tfstate-prod-2026`. 변경 시 이후 모든 step 의 버킷 이름 반영.

- [ ] **Step 3: 버킷 versioning 활성화**

Run:

```bash
aws s3api put-bucket-versioning \
  --bucket youthfit-tfstate-prod \
  --versioning-configuration Status=Enabled
```

검증:

```bash
aws s3api get-bucket-versioning --bucket youthfit-tfstate-prod
```

Expected: `{"Status": "Enabled"}` 출력.

- [ ] **Step 4: 버킷 public access 차단**

Run:

```bash
aws s3api put-public-access-block \
  --bucket youthfit-tfstate-prod \
  --public-access-block-configuration "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
```

검증:

```bash
aws s3api get-public-access-block --bucket youthfit-tfstate-prod
```

Expected: 4개 항목 모두 `true`.

- [ ] **Step 5: 버킷 기본 암호화 (SSE-S3)**

Run:

```bash
aws s3api put-bucket-encryption \
  --bucket youthfit-tfstate-prod \
  --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
```

- [ ] **Step 6: state lock 용 DynamoDB 테이블 생성**

Run:

```bash
aws dynamodb create-table \
  --table-name youthfit-tfstate-lock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region ap-northeast-2
```

Expected output: 테이블 정보 JSON, `"TableStatus": "CREATING"`.

- [ ] **Step 7: 테이블 active 까지 대기**

Run:

```bash
aws dynamodb wait table-exists --table-name youthfit-tfstate-lock
echo "DynamoDB table is ready"
```

대기 후 출력되면 통과.

- [ ] **Step 8: 통합 검증**

Run:

```bash
echo "=== S3 bucket ==="
aws s3api head-bucket --bucket youthfit-tfstate-prod && echo OK || echo FAIL

echo ""
echo "=== DynamoDB table ==="
aws dynamodb describe-table --table-name youthfit-tfstate-lock \
  --query 'Table.[TableName,TableStatus,BillingModeSummary.BillingMode]' \
  --output table
```

Expected:
- S3: `OK`
- DynamoDB: 1행, `TableStatus=ACTIVE`, `BillingMode=PAY_PER_REQUEST`

이 Task 는 git commit 없음 (인프라 외부 자원만 생성).

---

### Task 2: Terraform 프로젝트 디렉터리 + 기본 파일 생성

**Files:**
- Create: `infra/terraform/.gitignore`
- Create: `infra/terraform/versions.tf`
- Create: `infra/terraform/providers.tf`
- Create: `infra/terraform/backend.tf`
- Create: `infra/terraform/variables.tf`
- Create: `infra/terraform/outputs.tf`
- Create: `infra/terraform/terraform.tfvars.example`
- Create: `infra/README.md`

- [ ] **Step 1: 디렉터리 생성**

Run:

```bash
mkdir -p /Users/taetaetae/IdeaProjects/youthfit/infra/terraform
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform
```

- [ ] **Step 2: .gitignore 작성**

Create `infra/terraform/.gitignore`:

```gitignore
# Terraform 작업 디렉터리
.terraform/
.terraform.tfstate.lock.info

# state 파일 (S3 backend 사용해도 로컬에 임시 생성될 수 있음)
*.tfstate
*.tfstate.backup
*.tfstate.*.backup

# 시크릿이 들어가는 변수 파일
terraform.tfvars
*.tfvars
!terraform.tfvars.example

# crash logs
crash.log
crash.*.log

# 콘솔 plan 출력 임시 저장
*.tfplan
```

> `.terraform.lock.hcl` 은 **커밋 대상** (provider 버전 락). gitignore 에서 빠져있음.

- [ ] **Step 3: versions.tf 작성**

Create `infra/terraform/versions.tf`:

```hcl
terraform {
  required_version = ">= 1.7.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}
```

- [ ] **Step 4: providers.tf 작성**

Create `infra/terraform/providers.tf`:

```hcl
provider "aws" {
  region  = var.aws_region
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

- [ ] **Step 5: backend.tf 작성**

Create `infra/terraform/backend.tf`:

```hcl
terraform {
  backend "s3" {
    bucket         = "youthfit-tfstate-prod"
    key            = "prod/terraform.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "youthfit-tfstate-lock"
    encrypt        = true
    profile        = "youthfit-deploy"
  }
}
```

- [ ] **Step 6: variables.tf 작성**

Create `infra/terraform/variables.tf`:

```hcl
variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

variable "aws_profile" {
  type    = string
  default = "youthfit-deploy"
}

variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "azs" {
  type    = list(string)
  default = ["ap-northeast-2a", "ap-northeast-2c"]
}

variable "public_subnet_cidrs" {
  type    = list(string)
  default = ["10.20.1.0/24", "10.20.2.0/24"]
}

variable "private_subnet_cidrs" {
  type    = list(string)
  default = ["10.20.11.0/24", "10.20.12.0/24"]
}

variable "admin_ssh_cidr" {
  type        = string
  description = "SSH 접근 허용할 본인 공인 IP (/32 마스크 포함)"
  # 예: "203.0.113.5/32"
}

variable "db_username" {
  type    = string
  default = "youthfit"
}

variable "db_password" {
  type        = string
  sensitive   = true
  description = "RDS Postgres 마스터 비밀번호. 최소 16자 + 영숫자 + 특수문자. terraform.tfvars 에 저장 (gitignored)."
}

variable "db_name" {
  type    = string
  default = "youthfit"
}

variable "db_engine_version" {
  type        = string
  default     = "17.2"
  description = "RDS Postgres 엔진 버전. 사용 가능한 17.x 버전으로. 현재 시점 사용 가능 버전은 aws rds describe-db-engine-versions 로 확인."
}

variable "route53_zone_id" {
  type        = string
  description = "youthfit.xyz Route 53 호스팅 영역 ID. Plan A Task 3 Step 7 에서 확보."
  # 예: "Z03XXXXXXXXXXXXX"
}
```

- [ ] **Step 7: outputs.tf 작성**

Create `infra/terraform/outputs.tf`:

```hcl
output "vpc_id" {
  value = aws_vpc.main.id
}

output "public_subnet_ids" {
  value = [for s in aws_subnet.public : s.id]
}

output "private_subnet_ids" {
  value = [for s in aws_subnet.private : s.id]
}

output "web_security_group_id" {
  value = aws_security_group.web.id
}

output "db_security_group_id" {
  value = aws_security_group.db.id
}

output "web_eip" {
  value       = aws_eip.web.public_ip
  description = "EC2 에 부착 예정 EIP. Plan C 에서 EC2 attach 후 Route 53 A 레코드 대상."
}

output "rds_endpoint" {
  value       = aws_db_instance.main.endpoint
  description = "host:port 형태"
}

output "rds_address" {
  value       = aws_db_instance.main.address
  description = "host only (포트 제외)"
}
```

> `aws_vpc.main` 등 아직 정의 안 됐지만, Task 5 에서 정의되므로 plan/apply 전에는 에러 안 남. (terraform init 만 통과)

- [ ] **Step 8: terraform.tfvars.example 작성**

Create `infra/terraform/terraform.tfvars.example`:

```hcl
# 이 파일을 terraform.tfvars 로 복사하고 값을 채워서 사용.
# terraform.tfvars 는 gitignored.

aws_region      = "ap-northeast-2"
aws_profile     = "youthfit-deploy"
admin_ssh_cidr  = "203.0.113.5/32"          # 본인 공인 IP/32
db_password     = "REPLACE_WITH_STRONG_16+_CHAR_PASSWORD"
route53_zone_id = "Z03XXXXXXXXXXXXX"        # Plan A 에서 확보
```

- [ ] **Step 9: infra/README.md 작성**

Create `infra/README.md`:

```markdown
# YouthFit 인프라

Terraform 기반 prod 인프라 정의.

## 디렉터리

- `terraform/`: 모든 AWS 리소스 정의

## 사전 준비

1. `~/.aws/credentials` 에 `youthfit-deploy` 프로파일 설정 (docs/superpowers/plans/2026-05-23-aws-deployment-plan-a-prerequisites.md 참고)
2. Terraform 1.7+ 설치
3. `terraform/terraform.tfvars.example` 을 `terraform/terraform.tfvars` 로 복사 후 값 채움 (gitignored)

## 명령

```bash
cd infra/terraform
terraform init     # 처음 한 번
terraform plan     # 변경 미리보기
terraform apply    # 실제 적용
```

## 보안

- `terraform.tfvars` 는 절대 커밋하지 않는다.
- state 파일은 S3 (`youthfit-tfstate-prod`) + DynamoDB lock (`youthfit-tfstate-lock`) 으로 관리.
- DB 비밀번호는 1Password 등 비밀번호 관리자에 별도 보관 (tfvars 분실 대비).

## 관련 문서

- 설계: `docs/superpowers/specs/2026-05-23-aws-deployment-design.md`
- 실행 plan: `docs/superpowers/plans/2026-05-23-aws-deployment-plan-*.md`
```

- [ ] **Step 10: 파일 존재 확인**

Run:

```bash
ls -la /Users/taetaetae/IdeaProjects/youthfit/infra/terraform/
ls -la /Users/taetaetae/IdeaProjects/youthfit/infra/
```

Expected: 8개 파일 (.gitignore, versions.tf, providers.tf, backend.tf, variables.tf, outputs.tf, terraform.tfvars.example, README.md 부모) 확인.

- [ ] **Step 11: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add infra/
git commit -m "feat(infra): scaffold Terraform project for prod deployment

Empty modules + variables + outputs declarations. Resources will be
added in subsequent tasks. terraform.tfvars is gitignored.

Plan B Task 2."
```

---

### Task 3: 본인 IP 확인 + `terraform.tfvars` 작성

**Files:**
- Create: `infra/terraform/terraform.tfvars` (**gitignored — 커밋 금지**)

- [ ] **Step 1: 본인 공인 IP 확인**

Run:

```bash
curl -s https://api.ipify.org
echo ""
```

Expected: 본인 공인 IP 출력 (예: `203.0.113.5`).

> 카페·VPN·통신사에 따라 IP 가 자주 바뀔 수 있음. SSH 가 막히면 이 값만 갱신하고 `terraform apply` 재실행.

- [ ] **Step 2: DB 비밀번호 생성**

Run:

```bash
openssl rand -base64 24 | tr -d '/+=' | head -c 24
echo ""
```

Expected: 24자 영숫자 비밀번호 (예: `aBcDeF1234gHiJkLmNoPqRsTuV`).

이 값을 1Password 등 비밀번호 관리자에 즉시 저장.

- [ ] **Step 3: terraform.tfvars 작성**

Create `infra/terraform/terraform.tfvars` (값은 위에서 확보한 실제 값으로 치환):

```hcl
aws_region      = "ap-northeast-2"
aws_profile     = "youthfit-deploy"
admin_ssh_cidr  = "203.0.113.5/32"                # Step 1 결과 + /32
db_password     = "aBcDeF1234gHiJkLmNoPqRsTuV"    # Step 2 결과
route53_zone_id = "Z03XXXXXXXXXXXXX"              # Plan A Task 3 Step 7 결과
```

- [ ] **Step 4: 파일이 gitignored 인지 확인**

Run:

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git status infra/terraform/terraform.tfvars
```

Expected: 파일이 status 에 **나타나지 않음** (gitignore 작동). 만약 나타나면 즉시 STOP, `.gitignore` 재확인.

추가 검증:

```bash
git check-ignore -v infra/terraform/terraform.tfvars
```

Expected: `infra/terraform/.gitignore:7:terraform.tfvars infra/terraform/terraform.tfvars` 비슷한 출력 (라인 번호 다를 수 있음).

이 Task 는 git commit 없음 (시크릿만 로컬 파일에).

---

### Task 4: `terraform init` (S3 백엔드 활성화)

**Files:**
- Create: `infra/terraform/.terraform.lock.hcl` (커밋 대상)
- Create: `infra/terraform/.terraform/` (gitignored 디렉터리)

- [ ] **Step 1: terraform init 실행**

Run:

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform
terraform init
```

Expected output 핵심:
```
Initializing the backend...
Successfully configured the backend "s3"!
...
Initializing provider plugins...
- Installing hashicorp/aws v5.x.x...
- Installing hashicorp/random v3.x.x...
...
Terraform has been successfully initialized!
```

- [ ] **Step 2: lock 파일 확인**

Run:

```bash
ls -la /Users/taetaetae/IdeaProjects/youthfit/infra/terraform/.terraform.lock.hcl
```

Expected: 파일 존재.

- [ ] **Step 3: state 가 S3 에 빈 상태로 있는지 확인**

Run:

```bash
aws s3 ls s3://youthfit-tfstate-prod/prod/
```

Expected: `terraform.tfstate` 파일이 있거나, 빈 상태 (아직 apply 안 했으므로 state 가 막 비어있을 수 있음).

- [ ] **Step 4: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add infra/terraform/.terraform.lock.hcl
git commit -m "chore(infra): commit Terraform provider lock file

Pins AWS provider 5.x and random 3.x for reproducible builds.

Plan B Task 4."
```

---

### Task 5: 네트워크 리소스 정의 (VPC, 서브넷, IGW, RT, SG, EIP)

**Files:**
- Create: `infra/terraform/network.tf`

- [ ] **Step 1: network.tf 작성**

Create `infra/terraform/network.tf`:

```hcl
# ──────────── VPC ────────────
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "youthfit-prod-vpc"
  }
}

# ──────────── Public subnets (EC2, 향후 ALB) ────────────
resource "aws_subnet" "public" {
  for_each = { for idx, az in var.azs : idx => az }

  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidrs[each.key]
  availability_zone       = each.value
  map_public_ip_on_launch = false

  tags = {
    Name = "youthfit-prod-public-${each.value}"
    Tier = "public"
  }
}

# ──────────── Private subnets (RDS) ────────────
resource "aws_subnet" "private" {
  for_each = { for idx, az in var.azs : idx => az }

  vpc_id            = aws_vpc.main.id
  cidr_block        = var.private_subnet_cidrs[each.key]
  availability_zone = each.value

  tags = {
    Name = "youthfit-prod-private-${each.value}"
    Tier = "private"
  }
}

# ──────────── Internet Gateway + Public Route Table ────────────
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "youthfit-prod-igw"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name = "youthfit-prod-rt-public"
  }
}

resource "aws_route_table_association" "public" {
  for_each       = aws_subnet.public
  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

# ──────────── Private Route Table (NAT 없음) ────────────
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "youthfit-prod-rt-private"
  }
}

resource "aws_route_table_association" "private" {
  for_each       = aws_subnet.private
  subnet_id      = each.value.id
  route_table_id = aws_route_table.private.id
}

# ──────────── Security Groups ────────────
resource "aws_security_group" "web" {
  name        = "youthfit-web-sg"
  description = "Backend EC2: SSH (admin IP only), HTTP, HTTPS"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "SSH from admin"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.admin_ssh_cidr]
  }

  ingress {
    description = "HTTP (Caddy 가 redirect)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS (Caddy)"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "youthfit-web-sg"
  }
}

resource "aws_security_group" "db" {
  name        = "youthfit-db-sg"
  description = "RDS Postgres: 5432 from web SG only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Postgres from web SG"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.web.id]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "youthfit-db-sg"
  }
}

# ──────────── Elastic IP for backend EC2 (attach in Plan C) ────────────
resource "aws_eip" "web" {
  domain = "vpc"

  tags = {
    Name = "youthfit-web-eip"
  }
}
```

- [ ] **Step 2: terraform validate**

Run:

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform
terraform validate
```

Expected: `Success! The configuration is valid.`

문법 오류 나면 즉시 수정.

- [ ] **Step 3: terraform plan**

Run:

```bash
terraform plan -out=network.tfplan
```

Expected: 약 14개 리소스 `+ create` 표시. 마지막 줄:
```
Plan: 14 to add, 0 to change, 0 to destroy.
```

(VPC 1, public subnet 2, private subnet 2, IGW 1, public RT 1, public RT assoc 2, private RT 1, private RT assoc 2, web SG 1, db SG 1, EIP 1 = 14)

> 수가 약간 다를 수 있음(태그 차이 등). 14±2 면 OK.

- [ ] **Step 4: plan 검토 — 주요 리소스 미리보기**

Run:

```bash
terraform show -json network.tfplan | jq '.resource_changes[] | select(.change.actions[] == "create") | .address' | sort
```

Expected output (순서는 다를 수 있음):
```
"aws_eip.web"
"aws_internet_gateway.main"
"aws_route_table.private"
"aws_route_table.public"
"aws_route_table_association.private[\"0\"]"
"aws_route_table_association.private[\"1\"]"
"aws_route_table_association.public[\"0\"]"
"aws_route_table_association.public[\"1\"]"
"aws_security_group.db"
"aws_security_group.web"
"aws_subnet.private[\"0\"]"
"aws_subnet.private[\"1\"]"
"aws_subnet.public[\"0\"]"
"aws_subnet.public[\"1\"]"
"aws_vpc.main"
```

- [ ] **Step 5: terraform apply**

Run:

```bash
terraform apply network.tfplan
```

> plan 파일을 인자로 주면 추가 확인 없이 적용. 안전을 원하면 `terraform apply` (인자 없이) 후 yes 입력.

Expected (마지막 부분):
```
Apply complete! Resources: 14 added, 0 changed, 0 destroyed.

Outputs:

vpc_id = "vpc-0..."
public_subnet_ids = [...]
private_subnet_ids = [...]
web_security_group_id = "sg-0..."
db_security_group_id = "sg-0..."
web_eip = "13.124.xxx.xxx"
rds_endpoint = <known after apply>     # RDS 아직 안 만듦
rds_address = <known after apply>
```

> RDS outputs 는 `aws_db_instance.main` 미정의 상태에서 에러가 날 수 있음. 그 경우 outputs.tf 의 RDS output 2개를 `terraform plan` 단계에서만 잠시 주석 처리하고, Task 7 끝나면 복원. 또는 처음부터 분기 구성. 안정 경로로 가려면 outputs.tf 에서 `rds_*` 두 outputs 를 잠시 주석 처리하고, Task 7 직전에 복원.

⚠️ **위 outputs 에러 처리:** 만약 `terraform plan` 단계에서 `aws_db_instance.main` 참조 에러가 났다면:

1. `outputs.tf` 에서 `output "rds_endpoint"` 와 `output "rds_address"` 두 블록을 임시 주석 처리 (`/* ... */`)
2. `terraform plan -out=network.tfplan` 재실행
3. `terraform apply network.tfplan` 진행
4. Task 7 직전에 두 outputs 주석 해제

- [ ] **Step 6: 네트워크 검증 — AWS CLI 로 VPC/Subnet/SG 확인**

Run:

```bash
echo "=== VPC ==="
aws ec2 describe-vpcs --filters "Name=tag:Name,Values=youthfit-prod-vpc" \
  --query 'Vpcs[].[VpcId,CidrBlock,State]' --output table

echo ""
echo "=== Subnets ==="
aws ec2 describe-subnets --filters "Name=tag:Project,Values=youthfit" \
  --query 'Subnets[].[SubnetId,AvailabilityZone,CidrBlock,Tags[?Key==`Tier`].Value|[0]]' --output table

echo ""
echo "=== Security Groups ==="
aws ec2 describe-security-groups --filters "Name=tag:Project,Values=youthfit" \
  --query 'SecurityGroups[].[GroupId,GroupName,Description]' --output table

echo ""
echo "=== EIP ==="
aws ec2 describe-addresses --filters "Name=tag:Project,Values=youthfit" \
  --query 'Addresses[].[AllocationId,PublicIp,AssociationId]' --output table
```

Expected:
- VPC: 1개, CIDR `10.20.0.0/16`, State `available`
- Subnets: 4개 (public-2a, public-2c, private-2a, private-2c)
- SGs: youthfit-web-sg, youthfit-db-sg (+ default SG)
- EIP: 1개, AssociationId 비어있음 (Plan C 에서 EC2 attach)

- [ ] **Step 7: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add infra/terraform/network.tf infra/terraform/outputs.tf
git commit -m "feat(infra): provision VPC, subnets, security groups, EIP

- VPC 10.20.0.0/16 in ap-northeast-2
- Public subnets (10.20.1.0/24, 10.20.2.0/24) for EC2
- Private subnets (10.20.11.0/24, 10.20.12.0/24) for RDS
- Web SG: 22 (admin IP), 80, 443
- DB SG: 5432 from web SG only
- Elastic IP for backend EC2 (attach in Plan C)

Plan B Task 5."
```

> `network.tfplan` 파일은 .gitignore 에 의해 자동 제외.

---

### Task 6: RDS 리소스 정의 (subnet group, parameter group, instance)

**Files:**
- Create: `infra/terraform/rds.tf`
- Modify: `infra/terraform/outputs.tf` (Task 5 Step 5 에서 주석 처리했다면 복원)

- [ ] **Step 1: 사용 가능한 Postgres 17 마이너 버전 확인**

Run:

```bash
aws rds describe-db-engine-versions \
  --engine postgres \
  --query 'DBEngineVersions[?starts_with(EngineVersion,`17`)].EngineVersion' \
  --output text
```

Expected: `17.1 17.2 17.3` 비슷한 형태로 출력.

위 목록에서 **가장 최신 마이너** 버전 선택. 예: `17.5`.

> Plan B Task 2 의 `variables.tf` 에 `db_engine_version` 의 default 가 `17.2` 로 돼있음. 실제 사용 가능 버전과 다르면 `terraform.tfvars` 에 override 추가.

- [ ] **Step 2: 필요시 terraform.tfvars 에 엔진 버전 override 추가**

만약 Step 1 결과가 `17.2` 가 아니면 `terraform.tfvars` 에 다음 한 줄 추가:

```hcl
db_engine_version = "17.5"   # Step 1 에서 확인한 실제 사용 가능 버전
```

- [ ] **Step 3: pgvector 확장이 지원되는지 확인**

Run:

```bash
aws rds describe-db-engine-versions \
  --engine postgres \
  --engine-version "17.2" \
  --query 'DBEngineVersions[0].SupportedFeatureNames' \
  --output json
```

`SupportedFeatureNames` 가 비어있어도 OK — pgvector 는 trusted extension 이라 `CREATE EXTENSION` 으로 직접 활성. RDS Postgres 16+ 이면 모두 지원.

- [ ] **Step 4: rds.tf 작성**

Create `infra/terraform/rds.tf`:

```hcl
# ──────────── Subnet group ────────────
resource "aws_db_subnet_group" "main" {
  name       = "youthfit-prod-db-subnet"
  subnet_ids = [for s in aws_subnet.private : s.id]

  tags = {
    Name = "youthfit-prod-db-subnet"
  }
}

# ──────────── Parameter group ────────────
resource "aws_db_parameter_group" "pg17" {
  name        = "youthfit-prod-pg17"
  family      = "postgres17"
  description = "youthfit prod custom params"

  parameter {
    name         = "shared_preload_libraries"
    value        = "vector"
    apply_method = "pending-reboot"
  }

  parameter {
    name         = "log_min_duration_statement"
    value        = "500"
    apply_method = "immediate"
  }

  tags = {
    Name = "youthfit-prod-pg17"
  }
}

# ──────────── RDS instance ────────────
resource "aws_db_instance" "main" {
  identifier     = "youthfit-prod"
  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = "db.t3.micro"

  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  parameter_group_name   = aws_db_parameter_group.pg17.name

  multi_az            = false
  publicly_accessible = false

  backup_retention_period   = 7
  backup_window             = "18:00-19:00"               # KST 03:00-04:00
  maintenance_window        = "sun:19:30-sun:20:30"        # KST 일 04:30
  copy_tags_to_snapshot     = true

  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "youthfit-prod-final-snapshot"

  performance_insights_enabled = false
  monitoring_interval          = 0

  apply_immediately = false

  tags = {
    Name = "youthfit-prod-rds"
  }
}
```

- [ ] **Step 5: outputs.tf 의 RDS output 복원 (Task 5 에서 주석 처리했다면)**

`infra/terraform/outputs.tf` 에서 `output "rds_endpoint"` 와 `output "rds_address"` 가 주석 처리돼있다면 주석 해제.

- [ ] **Step 6: terraform validate**

Run:

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform
terraform validate
```

Expected: `Success! The configuration is valid.`

- [ ] **Step 7: terraform plan**

Run:

```bash
terraform plan -out=rds.tfplan
```

Expected:
- 3개 리소스 추가 (subnet group, parameter group, db instance)
- 기존 14개 리소스 변경 없음 (`0 to change, 0 to destroy`)
- 마지막: `Plan: 3 to add, 0 to change, 0 to destroy.`

- [ ] **Step 8: terraform apply (시간 소요 ~10분)**

Run:

```bash
terraform apply rds.tfplan
```

> RDS 생성은 보통 8-12분 소요. 진행 중에 다른 작업 가능.

Expected (마지막):
```
aws_db_instance.main: Still creating... [9m30s elapsed]
aws_db_instance.main: Creation complete after 10m12s

Apply complete! Resources: 3 added, 0 changed, 0 destroyed.

Outputs:

rds_endpoint = "youthfit-prod.cxxxxxxxxxx.ap-northeast-2.rds.amazonaws.com:5432"
rds_address  = "youthfit-prod.cxxxxxxxxxx.ap-northeast-2.rds.amazonaws.com"
... (네트워크 outputs 유지)
```

- [ ] **Step 9: RDS 검증 — AWS CLI**

Run:

```bash
aws rds describe-db-instances \
  --db-instance-identifier youthfit-prod \
  --query 'DBInstances[0].[DBInstanceStatus,Engine,EngineVersion,DBInstanceClass,Endpoint.Address,StorageEncrypted,DeletionProtection]' \
  --output table
```

Expected:
- `DBInstanceStatus`: `available`
- `Engine`: `postgres`
- `EngineVersion`: 17.x
- `DBInstanceClass`: `db.t3.micro`
- `Endpoint.Address`: RDS DNS
- `StorageEncrypted`: `True`
- `DeletionProtection`: `True`

- [ ] **Step 10: 커밋**

```bash
cd /Users/taetaetae/IdeaProjects/youthfit
git add infra/terraform/rds.tf infra/terraform/outputs.tf
git commit -m "feat(infra): provision RDS Postgres 17 with pgvector preload

- db.t3.micro Single-AZ
- gp3 20GB (autoscale to 100GB)
- encrypted at rest
- 7-day backups (03:00 KST window)
- shared_preload_libraries=vector for pgvector
- deletion_protection enabled

Plan B Task 6."
```

---

### Task 7: 결과 검증 + Plan B 종료 체크리스트

**Files:** (없음 — 검증만)

- [ ] **Step 1: 전체 outputs 확인**

Run:

```bash
cd /Users/taetaetae/IdeaProjects/youthfit/infra/terraform
terraform output
```

Expected (값은 환경 따라 다름):
```
db_security_group_id = "sg-0..."
private_subnet_ids = [ "subnet-...", "subnet-..." ]
public_subnet_ids  = [ "subnet-...", "subnet-..." ]
rds_address  = "youthfit-prod....rds.amazonaws.com"
rds_endpoint = "youthfit-prod....rds.amazonaws.com:5432"
vpc_id = "vpc-0..."
web_eip = "13.124.xxx.xxx"
web_security_group_id = "sg-0..."
```

- [ ] **Step 2: 다음 값을 메모 (Plan C 에서 사용)**

```
VPC_ID=<vpc_id>
PUBLIC_SUBNET_ID_A=<public_subnet_ids[0]>
PUBLIC_SUBNET_ID_C=<public_subnet_ids[1]>
WEB_SG_ID=<web_security_group_id>
DB_SG_ID=<db_security_group_id>
WEB_EIP=<web_eip>
RDS_ADDRESS=<rds_address>
DB_USERNAME=youthfit
DB_PASSWORD=<terraform.tfvars 의 db_password>
DB_NAME=youthfit
```

- [ ] **Step 3: state 파일 S3 보관 확인**

Run:

```bash
aws s3 ls s3://youthfit-tfstate-prod/prod/
```

Expected: `terraform.tfstate` 파일 존재, 크기 0 보다 큼.

- [ ] **Step 4: state lock 확인**

Run:

```bash
aws dynamodb scan --table-name youthfit-tfstate-lock --max-items 5
```

Expected: 보통 비어있음 (apply 중이 아니면). Items 가 있다면 정상 (이전 lock 흔적).

- [ ] **Step 5: 비용 추정**

이 시점에서 시간당 발생 비용 (Plan B 단독):
- RDS db.t3.micro: ~$0.020/시간 (= 월 ~$14)
- RDS gp3 20GB: ~$0.004/시간 (= 월 ~$3)
- EIP (현재 unattached): ~$0.005/시간 (= 월 ~$3.6) — Plan C 에서 EC2 attach 하면 무료
- S3 state bucket + DynamoDB: ~$0/시간 (사용량 미미)

**합계: 시간당 ~$0.029 = 일 ~$0.7 = 월 ~$21**

이 단계에서 Plan B 가 며칠 멈춰도 일 $0.7 만 소비. Plan C/D/E 로 빠르게 이어가는 게 비용·진척 모두 유리.

---

## Plan B 완료 조건

- [ ] `terraform output` 이 vpc_id, subnet_ids, eip, rds_endpoint 등 9개 output 출력
- [ ] `aws rds describe-db-instances --db-instance-identifier youthfit-prod` 가 `DBInstanceStatus=available`
- [ ] `aws ec2 describe-vpcs --filters "Name=tag:Name,Values=youthfit-prod-vpc"` 가 1행 반환
- [ ] git log 에 Plan B 관련 commit 4개 (Task 2, 4, 5, 6)
- [ ] `infra/terraform/terraform.tfvars` 는 gitignored (커밋 안 됨)
- [ ] state 가 S3 `youthfit-tfstate-prod/prod/terraform.tfstate` 에 존재
- [ ] Plan C 에서 사용할 값 메모 완료 (Task 7 Step 2)

## Plan B 시점의 알려진 미완료 작업

- pgvector `CREATE EXTENSION` 미실행 (RDS 에 접근 가능한 환경이 아직 없음 → **Plan C 의 첫 task** 로 EC2 가 뜨자마자 처리)
- 기존 `db/init/*.sql` 및 `backend/src/main/resources/sql/*.sql` 의 운영 PG 적용 → Plan C 의 한 task 로 처리
- EIP 미부착 → Plan C 에서 EC2 생성 시 attach
- Route 53 레코드 미등록 → Plan D 에서 처리

**다음 단계:** Plan C (EC2 + Caddy + ECR + SSM)
