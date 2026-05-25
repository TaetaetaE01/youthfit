# ──────────── Key pair (Plan A 의 SSH 공개키) ────────────
# SSH 공개키 내용을 ssh_public_key 변수로 전달.

resource "aws_key_pair" "admin" {
  key_name   = "youthfit-prod-admin"
  public_key = var.ssh_public_key

  tags = {
    Name = "youthfit-prod-admin"
  }
}

# ──────────── AMI: Amazon Linux 2023 최신 ────────────

data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# ──────────── User data (cloud-init) ────────────

locals {
  # user-data 는 EC2 의 시스템 환경(Docker + 디렉터리) 만 준비한다.
  # deploy/ 자산(docker-compose.prod.yml, Caddyfile, fetch-secrets.sh,
  # youthfit.service)과 컨테이너 이미지 push 는 Plan E 의 GitHub Actions
  # 가 SSH/scp 로 EC2 에 전달하는 표준 CI/CD 패턴을 사용한다.
  user_data = <<-USERDATA
    #!/bin/bash
    set -euxo pipefail
    exec > >(tee /var/log/youthfit-bootstrap.log) 2>&1

    # 1. 시스템 업데이트
    dnf update -y
    dnf install -y docker git jq amazon-cloudwatch-agent

    # 2. Docker
    systemctl enable --now docker
    usermod -aG docker ec2-user

    # 3. Docker Compose plugin (al2023 의 docker 패키지는 compose plugin 별도)
    DOCKER_CONFIG=$${DOCKER_CONFIG:-/usr/local/lib/docker}
    mkdir -p $DOCKER_CONFIG/cli-plugins
    curl -SL https://github.com/docker/compose/releases/download/v2.27.0/docker-compose-linux-x86_64 \
      -o $DOCKER_CONFIG/cli-plugins/docker-compose
    chmod +x $DOCKER_CONFIG/cli-plugins/docker-compose

    # 4. youthfit 디렉터리 (deploy 자산은 Plan E 의 CI/CD 가 scp 로 도착시킴)
    mkdir -p /opt/youthfit
    mkdir -p /etc/youthfit
    chmod 700 /etc/youthfit
    chown ec2-user:ec2-user /opt/youthfit

    echo "Bootstrap complete. Docker ready. deploy/ assets pending CI delivery."
  USERDATA
}

# ──────────── EC2 instance ────────────

resource "aws_instance" "web" {
  ami                    = data.aws_ami.al2023.id
  instance_type          = "t3.small"
  key_name               = aws_key_pair.admin.key_name
  iam_instance_profile   = aws_iam_instance_profile.ec2.name
  vpc_security_group_ids = [aws_security_group.web.id]
  subnet_id              = aws_subnet.public["0"].id

  # 퍼블릭 서브넷이지만 map_public_ip_on_launch=false 라 EIP 로만 접근.
  associate_public_ip_address = false

  user_data                   = local.user_data
  user_data_replace_on_change = false

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 30
    encrypted             = true
    delete_on_termination = true

    tags = {
      Name = "youthfit-prod-web-root"
    }
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required" # IMDSv2 only
    http_put_response_hop_limit = 2
  }

  tags = {
    Name = "youthfit-prod-web"
  }

  lifecycle {
    ignore_changes = [
      ami,        # AMI 갱신은 explicit 작업으로
      user_data,  # user-data 변경은 새 인스턴스 만들 때만 (Plan F)
      # AWS 가 public subnet 의 인스턴스에 자동 public IP 를 붙이고 그 위에 EIP 가
      # 덮어쓰는 형태로 state 에 true 로 잡힘. config 의 false 와 영구 drift 라
      # 무시. EIP association 으로만 public 접근 제어.
      associate_public_ip_address,
    ]
  }
}

# ──────────── EIP attach ────────────

resource "aws_eip_association" "web" {
  instance_id   = aws_instance.web.id
  allocation_id = aws_eip.web.id
}
