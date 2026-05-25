# ──────────── Subnet group (private subnets only) ────────────
resource "aws_db_subnet_group" "main" {
  name       = "youthfit-prod-db-subnet"
  subnet_ids = [for s in aws_subnet.private : s.id]

  tags = {
    Name = "youthfit-prod-db-subnet"
  }
}

# ──────────── Parameter group ────────────
# pgvector 는 RDS 의 trusted extension 이라 shared_preload_libraries 불필요.
# Plan C 에서 EC2 가 RDS 에 접속 후 `CREATE EXTENSION vector;` 한 번 실행하면 활성.
resource "aws_db_parameter_group" "pg17" {
  name        = "youthfit-prod-pg17"
  family      = "postgres17"
  description = "youthfit prod custom params"

  parameter {
    name         = "log_min_duration_statement"
    value        = "500"
    apply_method = "immediate"
  }

  tags = {
    Name = "youthfit-prod-pg17"
  }
}

# ──────────── RDS instance ────────────
resource "aws_db_instance" "main" {
  identifier     = "youthfit-prod"
  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = "db.t3.micro"

  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  parameter_group_name   = aws_db_parameter_group.pg17.name

  multi_az            = false
  publicly_accessible = false

  backup_retention_period   = 7
  backup_window             = "18:00-19:00"           # KST 03:00-04:00
  maintenance_window        = "sun:19:30-sun:20:30"   # KST 일 04:30
  copy_tags_to_snapshot     = true

  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "youthfit-prod-final-snapshot"

  performance_insights_enabled = false
  monitoring_interval          = 0

  apply_immediately = false

  tags = {
    Name = "youthfit-prod-rds"
  }
}
