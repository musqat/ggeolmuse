# Ggeolmuse Terraform Infrastructure

이 디렉토리는 Ggeolmuse 프로젝트의 AWS 인프라를 Terraform으로 관리합니다.

## 사전 요구사항

- Terraform >= 1.10 (backend 의 `use_lockfile` 때문)
- AWS CLI 설정 완료
- 적절한 AWS IAM 권한

## 사용법

### 1. EC2 Instance ID 확인

```bash
aws ec2 describe-instances --filters "Name=tag:Name,Values=ggeolmuse" --query 'Reservations[0].Instances[0].InstanceId' --output text
```

### 2. terraform.tfvars 파일 생성

```bash
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your EC2 instance ID
```

### 3. Terraform 초기화

```bash
terraform init
```

### 4. Plan 확인

```bash
terraform plan
```

### 5. Apply

```bash
terraform apply
```

## 관리되는 리소스

- IAM Role: `ggeolmuse-ec2-secrets-role`
- IAM Instance Profile: `ggeolmuse-ec2-profile`
- Instance Profile Association: EC2 인스턴스에 IAM Role 부착
- EventBridge 스케줄러: EC2 / RDS 평일 시작·중지 (`scheduler.tf`, `rds-scheduler.tf`)
- CloudWatch 알람 + SNS 이메일: 스케줄 실패 감지 (`monitoring.tf`)
- S3 버킷 `ggeolmuse-db-backup-apne2` + EC2 역할 쓰기 정책: 월간 pg_dump 보관 (`db-backup.tf`).
  CronJob 쪽은 `helm/ggeolmuse` 의 `dbBackup`

## 주의사항

- `terraform.tfvars` 파일은 Git에 커밋하지 마세요 (민감한 정보 포함)
- Production 환경에서는 반드시 `terraform plan`으로 변경사항을 확인한 후 apply하세요
- Terraform state 는 S3 `ggeolmuse-tfstate-apne2` 에 있다 (2026-09-08). 어느 기계에서든 `terraform init` 후 바로 쓴다
- EC2 에 실제로 붙은 인스턴스 프로파일은 콘솔에서 만든 `ggeolmuse-ec2-role` 이다. 이 저장소의 `ggeolmuse-ec2-profile` 은 만들어져 있지만 붙어 있지 않다. `db-backup.tf` 는 그래서 인스턴스에서 역할을 따라간다
