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
