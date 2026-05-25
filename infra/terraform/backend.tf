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
