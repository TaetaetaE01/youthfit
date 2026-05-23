# Plan A: AWS Deployment Prerequisites (Phase 0)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AWS IAM 사용자(`youthfit-deploy-admin`), SSH 키 페어, GitHub Secrets 슬롯을 모두 발급/문서화해서 Plan B+ 실행 가능 상태로 만든다.

**Architecture:** 모두 사용자가 직접 AWS 콘솔·로컬 터미널·GitHub 웹 UI 에서 수행하는 수동 작업. 코드 변경 없음. 각 Task 는 "사용자 수행 단계 + 검증 단계"로 구성된다.

**Tech Stack:** AWS Console (IAM), AWS CLI v2, ssh-keygen, GitHub web UI

**Pre-flight 체크:**
- AWS 계정 `596776566549` 에 root 또는 IAM 관리 권한 있는 사용자로 로그인 가능해야 함
- 로컬 머신에 AWS CLI v2 설치돼있음 (확인: `aws --version`)
- 로컬 머신에 ssh, ssh-keygen 사용 가능 (macOS 기본 포함)
- GitHub 리포지토리에 admin 권한 보유

**예상 소요:** 30~40분 (MFA 디바이스 등록 포함)

---

### Task 1: IAM 사용자 `youthfit-deploy-admin` 생성

**Files:** (AWS 콘솔 작업, 로컬 파일 변경 없음)

- [ ] **Step 1: AWS 콘솔 로그인**

URL: <https://596776566549.signin.aws.amazon.com/console>
리전: 우측 상단에서 **ap-northeast-2 (Seoul)** 선택

- [ ] **Step 2: IAM → Users → Create user**

좌측 메뉴: IAM → Users → 우측 상단 **Create user** 버튼

- [ ] **Step 3: 사용자 정보 입력**

- User name: `youthfit-deploy-admin`
- **Provide user access to the AWS Management Console - optional** 체크 (Terraform/CLI 만 쓸 거면 체크 안 해도 되지만, 콘솔도 동일 키로 들어갈 거면 체크)
- 콘솔 접근 체크한 경우:
  - User type: **I want to create an IAM user**
  - Console password: **Custom password** 입력 (강력한 비밀번호 사용)
  - **Users must create a new password at next sign-in**: **체크 해제**
- **Next** 클릭

- [ ] **Step 4: 권한 부여**

- **Attach policies directly** 선택
- 정책 검색창에 `AdministratorAccess` 입력
- 좌측 체크박스 활성화 (단 하나의 policy 만 선택)
- **Next** 클릭

- [ ] **Step 5: 검토 + 생성**

요약 페이지에서:
- User name: `youthfit-deploy-admin` 확인
- Permissions summary 에 `AdministratorAccess` 확인
- **Create user** 클릭

- [ ] **Step 6: 검증 — IAM 콘솔에서 사용자 존재 확인**

IAM → Users → 목록에서 `youthfit-deploy-admin` 클릭 → Permissions 탭에 `AdministratorAccess` 정책이 attached 상태로 표시되는지 확인.

> 이 시점에는 액세스 키가 없는 상태. Task 3 에서 발급.

---

### Task 2: MFA 디바이스 등록

**Files:** (AWS 콘솔 작업)

스마트폰에 **Google Authenticator** 또는 **Authy** 같은 TOTP 앱 미리 설치.

- [ ] **Step 1: 사용자 상세 화면 → Security credentials 탭**

IAM → Users → `youthfit-deploy-admin` → **Security credentials** 탭 클릭.

- [ ] **Step 2: Multi-factor authentication (MFA) 섹션 → Assign MFA device**

**Assign MFA device** 버튼 클릭.

- [ ] **Step 3: MFA 디바이스 정보 입력**

- Device name: `youthfit-deploy-admin-mfa-phone` (또는 본인이 식별 가능한 이름)
- MFA device: **Authenticator app** 선택
- **Next** 클릭

- [ ] **Step 4: QR 코드 스캔 + 코드 2회 입력**

- 화면의 QR 코드를 스마트폰의 Authenticator 앱으로 스캔
- 앱에 표시된 6자리 코드를 **MFA code 1** 에 입력
- 코드가 갱신될 때까지 30초 대기 후 새 코드를 **MFA code 2** 에 입력
- **Add MFA** 클릭

- [ ] **Step 5: 검증 — 사용자 상세 화면에서 MFA 디바이스 등록 확인**

Security credentials 탭의 Multi-factor authentication 섹션에 디바이스가 `Synced` 상태로 표시되는지 확인.

---

### Task 3: 액세스 키 발급 + 로컬 AWS CLI 프로파일 등록

**Files:**
- Modify: `~/.aws/credentials` (로컬 머신)
- Modify: `~/.aws/config` (로컬 머신)

- [ ] **Step 1: 사용자 상세 → Security credentials → Access keys → Create access key**

IAM → Users → `youthfit-deploy-admin` → **Security credentials** 탭 → **Access keys** 섹션 → **Create access key** 버튼.

- [ ] **Step 2: Use case 선택**

- **Command Line Interface (CLI)** 선택
- 하단 "I understand the above recommendation and want to proceed to create an access key." 체크
- **Next** 클릭

- [ ] **Step 3: 설명 태그(선택)**

- Description tag value: `local-mac-2026-05` (옵션, 식별용)
- **Create access key** 클릭

- [ ] **Step 4: 키 안전하게 보관 — 이 화면 닫으면 secret key 다시 못 봄**

- **Access key ID**: `AKIA...` (공개돼도 보안 영향 적음)
- **Secret access key**: `...` (절대 노출 금지, git 커밋 금지)
- 두 값을 메모장에 임시 복사 후 다음 Step 진행

- [ ] **Step 5: 로컬에 프로파일 추가**

로컬 터미널에서 `~/.aws/credentials` 파일에 다음 블록 추가 (기존 프로파일 아래에).

```ini
[youthfit-deploy]
aws_access_key_id = AKIA...
aws_secret_access_key = ...
```

그리고 `~/.aws/config` 파일에 다음 추가.

```ini
[profile youthfit-deploy]
region = ap-northeast-2
output = json
```

- [ ] **Step 6: 검증 — STS 로 신원 확인**

Run:

```bash
aws sts get-caller-identity --profile youthfit-deploy
```

Expected output:

```json
{
    "UserId": "AIDA...",
    "Account": "596776566549",
    "Arn": "arn:aws:iam::596776566549:user/youthfit-deploy-admin"
}
```

`Arn` 끝이 `user/youthfit-deploy-admin` 으로 나오면 성공.

- [ ] **Step 7: 추가 검증 — Route 53 권한 동작**

Run:

```bash
aws route53 list-hosted-zones --profile youthfit-deploy --query 'HostedZones[?Name==`youthfit.xyz.`].[Id,Name,Config.PrivateZone]' --output table
```

Expected: `youthfit.xyz.` 호스팅 영역의 ID 출력. (Plan B 에서 이 ID 가 필요함)

호스팅 영역 ID 형식 예: `/hostedzone/Z03XXXXXXXXXXXXX`

이 ID 를 임시 메모.

- [ ] **Step 8: 안전 정리 — 메모장에서 secret key 제거**

브라우저/메모장에서 Secret access key 가 복사된 부분을 모두 지운다. `~/.aws/credentials` 만 유일한 보관 장소가 되도록.

---

### Task 4: 운영용 SSH 키 페어 생성

**Files:**
- Create: `~/.ssh/youthfit_prod_ed25519` (private key)
- Create: `~/.ssh/youthfit_prod_ed25519.pub` (public key)

- [ ] **Step 1: SSH 키 페어 생성 (ed25519)**

Run:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/youthfit_prod_ed25519 -C "youthfit-prod-ec2-2026-05" -N ""
```

> `-N ""` 는 passphrase 없이 생성. GitHub Actions 가 사용할 거라 passphrase 없이 발급. 개인 사용용으론 passphrase 권장이지만 자동화엔 부적합.

Expected output: 키 생성 메시지 + fingerprint 표시.

- [ ] **Step 2: 권한 확인**

Run:

```bash
ls -la ~/.ssh/youthfit_prod_ed25519*
```

Expected:
```
-rw-------  ...  youthfit_prod_ed25519       (600 권한)
-rw-r--r--  ...  youthfit_prod_ed25519.pub   (644 권한)
```

권한이 다르면:

```bash
chmod 600 ~/.ssh/youthfit_prod_ed25519
chmod 644 ~/.ssh/youthfit_prod_ed25519.pub
```

- [ ] **Step 3: SSH config 에 식별자 추가**

`~/.ssh/config` 파일에 다음 추가 (host 는 Plan C 에서 EC2 EIP 가 정해지면 갱신):

```
Host youthfit-prod
    User ec2-user
    IdentityFile ~/.ssh/youthfit_prod_ed25519
    StrictHostKeyChecking accept-new
    # HostName 은 Plan C 에서 EIP 결정 후 추가
```

- [ ] **Step 4: 검증 — 공개키 내용 확인**

Run:

```bash
cat ~/.ssh/youthfit_prod_ed25519.pub
```

Expected: `ssh-ed25519 AAAA... youthfit-prod-ec2-2026-05` 형태로 한 줄 출력.

이 공개키 문자열을 임시 메모 (Plan C 의 EC2 key pair 등록 시 사용).

---

### Task 5: GitHub Secrets 슬롯 사전 정의

**Files:** (GitHub 웹 UI 작업, 코드 변경 없음)

> 이 단계에서는 **실제 값을 안 넣는다.** 어떤 secret 이름이 필요할지 슬롯만 미리 만들어둔다. 값은 Plan C/E 진행하면서 채운다.

- [ ] **Step 1: GitHub 리포지토리 → Settings → Secrets and variables → Actions**

리포지토리 URL 의 Settings 탭 → 좌측 메뉴 **Secrets and variables** → **Actions** 클릭.

- [ ] **Step 2: 다음 secret 슬롯을 빈 placeholder 값으로 생성**

각각 **New repository secret** 클릭 후 다음 이름으로 등록. 값은 임시로 `__TO_BE_FILLED__` 로 둠.

| Secret 이름 | 용도 | 채워질 시점 |
|------------|------|------------|
| `AWS_DEPLOY_ACCESS_KEY_ID` | GitHub Actions 가 ECR push·S3 sync·CloudFront invalidation 용 | Plan E |
| `AWS_DEPLOY_SECRET_ACCESS_KEY` | 위 키의 secret | Plan E |
| `AWS_REGION` | `ap-northeast-2` (지금 값 채워도 됨) | Plan E |
| `EC2_HOST` | 백엔드 EC2 의 EIP | Plan C 끝나는 시점 |
| `EC2_USER` | `ec2-user` (지금 값 채워도 됨) | Plan E |
| `EC2_SSH_PRIVATE_KEY` | `~/.ssh/youthfit_prod_ed25519` 내용 전체 | Plan C 끝나는 시점 |
| `ECR_REPOSITORY` | `youthfit-backend` (지금 값 채워도 됨) | Plan E |
| `S3_BUCKET_FRONTEND` | `youthfit-web-prod` (지금 값 채워도 됨) | Plan E |
| `CLOUDFRONT_DISTRIBUTION_ID` | CloudFront 배포 ID | Plan D 끝나는 시점 |

- [ ] **Step 3: 검증**

Settings → Secrets and variables → Actions 페이지에 위 9개 이름이 모두 보이면 성공.

> AWS 액세스 키는 **GitHub Actions 전용 별도 IAM 사용자** (`youthfit-gha-deploy`) 를 Plan E 에서 만들어 권한 좁힘 (ECR push, S3 sync, CloudFront invalidation 만). `youthfit-deploy-admin` 키를 직접 GitHub 에 넣지 않음.

---

### Task 6: 사전 준비 완료 체크리스트

**Files:** (없음 — 본인 검증)

- [ ] **Step 1: 최종 검증 명령 실행**

Run:

```bash
echo "=== AWS profile ==="
aws sts get-caller-identity --profile youthfit-deploy

echo ""
echo "=== Route 53 hosted zone for youthfit.xyz ==="
aws route53 list-hosted-zones --profile youthfit-deploy --query 'HostedZones[?Name==`youthfit.xyz.`]' --output json

echo ""
echo "=== SSH key fingerprint ==="
ssh-keygen -l -f ~/.ssh/youthfit_prod_ed25519.pub

echo ""
echo "=== MFA device on user (should show 1+ device) ==="
aws iam list-mfa-devices --user-name youthfit-deploy-admin --profile youthfit-deploy
```

Expected (각 섹션):
- AWS profile: ARN 끝이 `user/youthfit-deploy-admin`
- Route 53: youthfit.xyz 호스팅 영역이 1개 출력 (Id, Name, CallerReference 등)
- SSH key fingerprint: ed25519 fingerprint 한 줄
- MFA device: 등록한 디바이스 1개 출력

- [ ] **Step 2: 다음 정보를 별도 메모에 정리 (Plan B 에서 사용)**

```
ROUTE53_ZONE_ID=Z03XXXXXXXXXXXXX (Task 3 Step 7 에서 확보한 값)
SSH_PUBKEY="<~/.ssh/youthfit_prod_ed25519.pub 내용 한 줄>"
AWS_PROFILE=youthfit-deploy
AWS_REGION=ap-northeast-2
```

> 이 값들은 Plan B 의 `terraform.tfvars` 에 들어간다. `terraform.tfvars` 는 git ignore 대상.

---

## Plan A 완료 조건

- [ ] `aws sts get-caller-identity --profile youthfit-deploy` 가 `user/youthfit-deploy-admin` 반환
- [ ] MFA 디바이스 1개 등록 완료
- [ ] `~/.ssh/youthfit_prod_ed25519` + `.pub` 발급 완료
- [ ] GitHub Secrets 9개 슬롯 placeholder 생성 완료
- [ ] Route 53 호스팅 영역 ID 메모 완료

**다음 단계:** Plan B (Terraform 기반 + 네트워크 + DB) 실행
