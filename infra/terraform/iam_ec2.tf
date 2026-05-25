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
