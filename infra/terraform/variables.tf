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
  description = "SSH 접근 허용할 본인 공인 IP (/32 마스크 포함). 예: 203.0.113.5/32"
}

variable "db_username" {
  type    = string
  default = "youthfit"
}

variable "db_password" {
  type        = string
  sensitive   = true
  description = "RDS Postgres 마스터 비밀번호. 최소 16자. terraform.tfvars 에 저장 (gitignored)."
}

variable "db_name" {
  type    = string
  default = "youthfit"
}

variable "db_engine_version" {
  type        = string
  default     = "17.10"
  description = "RDS Postgres 엔진 버전. 2026-05-25 기준 17.2~17.10 사용 가능. 보안 패치 위해 최신 마이너 기본값."
}

variable "route53_zone_id" {
  type        = string
  description = "youthfit.xyz Route 53 호스팅 영역 ID. 예: Z05811777WNU2LJAW6QF"
}

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
