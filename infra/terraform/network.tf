# ──────────── VPC ────────────
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "youthfit-prod-vpc"
  }
}

# ──────────── Public subnets (EC2, 향후 ALB) ────────────
resource "aws_subnet" "public" {
  for_each = { for idx, az in var.azs : idx => az }

  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidrs[tonumber(each.key)]
  availability_zone       = each.value
  map_public_ip_on_launch = false

  tags = {
    Name = "youthfit-prod-public-${each.value}"
    Tier = "public"
  }
}

# ──────────── Private subnets (RDS) ────────────
resource "aws_subnet" "private" {
  for_each = { for idx, az in var.azs : idx => az }

  vpc_id            = aws_vpc.main.id
  cidr_block        = var.private_subnet_cidrs[tonumber(each.key)]
  availability_zone = each.value

  tags = {
    Name = "youthfit-prod-private-${each.value}"
    Tier = "private"
  }
}

# ──────────── Internet Gateway + Public Route Table ────────────
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "youthfit-prod-igw"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name = "youthfit-prod-rt-public"
  }
}

resource "aws_route_table_association" "public" {
  for_each       = aws_subnet.public
  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

# ──────────── Private Route Table (NAT 없음 — RDS 만 사용, 인터넷 불필요) ────────────
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "youthfit-prod-rt-private"
  }
}

resource "aws_route_table_association" "private" {
  for_each       = aws_subnet.private
  subnet_id      = each.value.id
  route_table_id = aws_route_table.private.id
}

# ──────────── Security Groups ────────────
resource "aws_security_group" "web" {
  name        = "youthfit-web-sg"
  description = "Backend EC2: SSH (admin IP only), HTTP, HTTPS"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "SSH from admin"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.admin_ssh_cidr]
  }

  ingress {
    description = "HTTP (Caddy redirect)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS (Caddy)"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "youthfit-web-sg"
  }
}

resource "aws_security_group" "db" {
  name        = "youthfit-db-sg"
  description = "RDS Postgres: 5432 from web SG only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Postgres from web SG"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.web.id]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "youthfit-db-sg"
  }
}

# ──────────── Elastic IP for backend EC2 (Plan C 에서 attach) ────────────
resource "aws_eip" "web" {
  domain = "vpc"

  tags = {
    Name = "youthfit-web-eip"
  }
}
