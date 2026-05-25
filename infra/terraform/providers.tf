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
