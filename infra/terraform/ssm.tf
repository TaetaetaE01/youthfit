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
