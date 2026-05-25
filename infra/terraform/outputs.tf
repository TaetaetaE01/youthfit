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

# RDS outputs — rds.tf 가 정의되기 전까지는 주석 처리. Task 6 직전 복원.
# output "rds_endpoint" {
#   value       = aws_db_instance.main.endpoint
#   description = "host:port 형태"
# }
#
# output "rds_address" {
#   value       = aws_db_instance.main.address
#   description = "host only"
# }
