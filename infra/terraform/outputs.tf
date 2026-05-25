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
  description = "host only"
}

output "ecr_backend_url" {
  value       = aws_ecr_repository.backend.repository_url
  description = "ECR 레포 전체 URL (e.g. 379197597410.dkr.ecr.ap-northeast-2.amazonaws.com/youthfit-backend)"
}

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

output "acm_certificate_arn" {
  value       = aws_acm_certificate_validation.cloudfront.certificate_arn
  description = "us-east-1 ACM 인증서 ARN (CloudFront 용)"
}

output "s3_web_bucket" {
  value       = aws_s3_bucket.web.id
  description = "프론트엔드 정적 자산 버킷 (Vite build sync 대상)"
}

output "s3_web_bucket_arn" {
  value = aws_s3_bucket.web.arn
}

output "cloudfront_distribution_id" {
  value       = aws_cloudfront_distribution.web.id
  description = "CloudFront invalidation 시 사용"
}

output "cloudfront_domain_name" {
  value       = aws_cloudfront_distribution.web.domain_name
  description = "예: d1234abcd.cloudfront.net (Route 53 Alias 의 dns_name)"
}

output "cloudfront_hosted_zone_id" {
  value       = aws_cloudfront_distribution.web.hosted_zone_id
  description = "Route 53 Alias 의 zone_id (CloudFront 전역 상수 Z2FDTNDATAQYW2)"
}
