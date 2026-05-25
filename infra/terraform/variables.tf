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
  default     = "17.2"
  description = "RDS Postgres 엔진 버전. aws rds describe-db-engine-versions 로 사용 가능 버전 확인."
}

variable "route53_zone_id" {
  type        = string
  description = "youthfit.xyz Route 53 호스팅 영역 ID. 예: Z05811777WNU2LJAW6QF"
}
