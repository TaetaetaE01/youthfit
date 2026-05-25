# YouthFit 배포 자산

이 디렉터리는 prod EC2 에 배치되는 컨테이너 / 부팅 설정 파일을 담는다.

## 파일

| 파일 | 역할 |
|------|------|
| `docker-compose.prod.yml` | backend + caddy + redis 정의 |
| `Caddyfile` | TLS 종료 + api.youthfit.xyz 라우팅 |
| `fetch-secrets.sh` | SSM → `/etc/youthfit/.env` |
| `youthfit.service` | systemd unit (부팅 시 compose 자동 기동) |

## 배포 흐름

1. EC2 부팅 → cloud-init user-data 가 `git clone` 으로 이 디렉터리 동기화 (`/opt/youthfit/`)
2. `youthfit.service` enable + start
3. `fetch-secrets.sh` 가 SSM 에서 시크릿 받아 `/etc/youthfit/.env` 작성 (600 권한)
4. ECR login → `docker compose pull && up -d`
5. Caddy 가 Let's Encrypt 인증서 자동 발급
6. backend health: `curl https://api.youthfit.xyz/actuator/health`

## 보안 노트

- `/etc/youthfit/.env` 는 root:root 600. 컨테이너만 env_file 로 읽음
- Caddy 는 HTTP-01 챌린지 → 포트 80 인바운드 필수 (SG 설정 완료)
- SSM 접근은 EC2 인스턴스 프로파일(`youthfit-ec2-profile`)을 통해서만

## 제외된 컴포넌트

- **n8n**: 로컬 E2E 테스트 완료 후 별도 plan 에서 추가
  - docker-compose.prod.yml 에 service block 추가
  - Caddyfile 에 `n8n.youthfit.xyz` 라우팅 추가
  - ECR 에 `youthfit-n8n` repo 생성
  - SSM 에 `/youthfit/prod/n8n/*` 슬롯 추가
- **postgres**: RDS 매니지드 (`youthfit-prod`)
