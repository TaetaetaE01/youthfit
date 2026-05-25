# YouthFit 인프라

Terraform 기반 prod 인프라 정의.

## 디렉터리

- `terraform/` — 모든 AWS 리소스 정의

## 사전 준비

1. `~/.aws/credentials` 에 `youthfit-deploy` 프로파일 설정
   ([Plan A](../docs/superpowers/plans/2026-05-23-aws-deployment-plan-a-prerequisites.md) 참고)
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

- 설계: [`docs/superpowers/specs/2026-05-23-aws-deployment-design.md`](../docs/superpowers/specs/2026-05-23-aws-deployment-design.md)
- 실행 plan: [`docs/superpowers/plans/`](../docs/superpowers/plans/)
