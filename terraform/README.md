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
# 값은 아래 "운영 자원 가져오기" 의 표
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

- 운영 핵심 자원 (`core.tf`, 가져오는 블록은 `imports.tf`)
  - EC2 `ggeolmuse`(t3a.xlarge, IMDSv2 만 · hop 2)와 보안 그룹 `ggeolmuse-sg`(443 은 접두사 목록 `cloudflare-ipv4` 만)
  - 노드 역할 · 인스턴스 프로파일 `ggeolmuse-ec2-role`(콘솔에서 만들어 EC2 에 연결된 것), 관리 정책 목록 전체와 시크릿 두 개 읽기
  - RDS `rds_instance_id`(PostgreSQL 15 · db.t3.micro · Single-AZ)
  - 상태 버킷 `ggeolmuse-tfstate-apne2`(버전 관리 · SSE-S3 · 퍼블릭 차단)
  - 모두 `prevent_destroy` 라 plan 이 교체 · 삭제를 잡으면 오류로 멈춘다
- EventBridge 스케줄러: EC2 / RDS 평일 시작·중지 (`scheduler.tf`, `rds-scheduler.tf`)
- CloudWatch 알람 + SNS 이메일: 스케줄 실패 감지 (`monitoring.tf`)
- EventBridge 규칙 + API Destination: EC2 가 멈추면 슬랙에 `down` 한 줄 (`ec2-stop-alert.tf`). `slack_webhook_url` 이 비면
  만들지 않고, 슬랙에 못 보내면 위 SNS 알람으로 온다
- S3 버킷 `ggeolmuse-db-backup-apne2` + EC2 역할 쓰기 정책: 월간 pg_dump 보관 (`db-backup.tf`).
  CronJob 쪽은 `helm/ggeolmuse` 의 `dbBackup`
- EventBridge Scheduler(그룹 `ggeolmuse-uptime`) + 규칙 + API Destination: 가동 시간(EC2 시작 20분 뒤부터 중지 전 정시까지)에
  5분마다 프론트 · 시세 API · /auth 를 AWS 에서 GET 으로 확인 (`uptime-check.tf`). 응답 본문은 보지 않고, 2xx 가 아니거나 시간 초과면
  주소별 CloudWatch 알람이 ALARM 이 되고 슬랙에 실패 · 복구 한 줄씩, SNS 이메일(`alarm_email`)로도 온다. 알람은 확인이 있었던 5분 구간만
  세어 EC2 가 꺼진 시간에는 상태가 그대로고 알림도 없다(실패한 채 꺼지면 다음 날 첫 확인이 성공할 때 복구 한 줄). 일정이 이벤트를 넣지
  못하면 그룹 알람이 SNS 로 온다. 주소는 `uptime_check_urls`, `slack_webhook_url` 이 비면 만들지 않는다

멀티 노드 예비 클러스터는 이 루트에 없다. 상태를 따로 둔 [`k8s/terraform`](../k8s/terraform) 루트에 있고, 올리는 순서는 [k8s/README.md](../k8s/README.md).

문법 검사와 가짜 AWS 프로바이더 테스트(`tests/*.tftest.hcl`, AWS 에 닿지 않음)는 아래 한 줄이다. 멀티 노드 쪽 테스트는
[k8s/README.md](../k8s/README.md#파일) 에 있다.

```bash
terraform init -backend=false && terraform validate && terraform test
```

## 운영 자원 가져오기

콘솔에서 만든 EC2 · 보안 그룹 · 노드 역할 · RDS · 상태 버킷을 `imports.tf` 의 import 블록으로 상태에 넣는다. 한 번 가져온
자원의 import 블록은 아무 일도 하지 않는다.

1. 아래 표의 값을 `terraform.tfvars` 에 채운다. 교체가 걸린 값은 plan 전에 명령으로 확인한다
2. `terraform plan` 을 본다. 교체가 잡히면 `prevent_destroy` 오류로 멈추므로 표에서 "다르면 교체" 인 값을 다시 본다
3. 바꾸겠다고 나온 값은 실제 값을 tfvars(변수가 없으면 코드)로 옮긴다. 남는 것이 import 와 태그 더하기 · 노드 역할의 시크릿 읽기
   정책 하나 · 연결 안 된 옛 역할 `ggeolmuse-ec2-secrets-role` · 프로파일 `ggeolmuse-ec2-profile` · 옛 역할의 정책 지우기 셋일 때 `terraform apply`
4. 다시 `terraform plan` 이 `No changes.` 인지 본다

| 변수 | 다르면 | 확인 명령 |
|---|---|---|
| `ec2_ami_id` · `ec2_subnet_id` · `ec2_key_name` | 서브넷만 교체(AMI · 키는 무시) | `aws ec2 describe-instances --instance-ids <ID> --query 'Reservations[0].Instances[0].[ImageId,SubnetId,KeyName,VpcId,InstanceType,SecurityGroups[].GroupId]' --output text` |
| `ec2_instance_type` | apply 가 노드를 멈췄다 다시 켬 | 위 명령의 `InstanceType` |
| `ec2_extra_security_group_ids` | 목록에 없는 그룹을 EC2 에서 뺌 | 위 명령의 보안 그룹 가운데 `ggeolmuse-sg` 를 뺀 나머지 |
| `ec2_root_volume_size` · `ec2_root_volume_type` | 교체 없이 바뀜(크기는 줄일 수 없음) | `aws ec2 describe-volumes --filters Name=attachment.instance-id,Values=<ID> --query 'Volumes[].[Size,VolumeType]' --output text` |
| `ec2_security_group_id` · `ec2_security_group_description` · `vpc_id` | 설명 · VPC 는 교체 | `aws ec2 describe-security-groups --filters Name=group-name,Values=ggeolmuse-sg --query 'SecurityGroups[0].[GroupId,Description,VpcId]' --output text` |
| `ec2_extra_ingress` · `cloudflare_rule_description` | 규칙을 지우고 다시 만듦 | 위 명령의 `--query 'SecurityGroups[0].IpPermissions'` |
| `node_role_policy_arns` | 목록에 없는 관리 정책을 역할에서 뺌 | `aws iam list-attached-role-policies --role-name ggeolmuse-ec2-role --query 'AttachedPolicies[].PolicyArn'` |
| `node_role_name` · `node_instance_profile_name` | EC2 에 연결되지 않은 역할을 가져옴 | `aws iam get-instance-profile --instance-profile-name ggeolmuse-ec2-role --query 'InstanceProfile.Roles[].RoleName'` |
| `rds_*` | 암호화 · 사용자 이름은 교체, 나머지는 교체 없이 바뀜 | `aws rds describe-db-instances --db-instance-identifier ggeolmuse-postgres --query 'DBInstances[0].[AllocatedStorage,StorageType,StorageEncrypted,PubliclyAccessible,DBSubnetGroup.DBSubnetGroupName,VpcSecurityGroups[].VpcSecurityGroupId]'` |

- `node_role_policy_arns` 는 처음에는 지금 목록 그대로 둔다. 시크릿 읽기는 인라인 정책으로 따로 들어가므로, 넓은 관리 정책
  (`SecretsManagerReadWrite` 등)은 apply 뒤 ExternalSecret 이 `SecretSynced` 인 것을 보고 목록에서 뺀다
- RDS 보안 그룹의 인바운드가 출발지로 가리키는 그룹은 `aws ec2 describe-security-groups --group-ids <rds_security_group_ids>
  --query 'SecurityGroups[].IpPermissions[].UserIdGroupPairs[].GroupId' --output text` 로 본다. 출발지 그룹이 EC2 에 달려 있는데
  `ec2_extra_security_group_ids` 에 없으면 apply 가 EC2 에서 빼 노드에서 RDS 5432 로 가는 연결이 거부된다
- 옛 역할 · 프로파일을 지우기 전에 `aws ec2 describe-iam-instance-profile-associations --query
  'IamInstanceProfileAssociations[].IamInstanceProfile.Arn' --output text` 에 `ggeolmuse-ec2-profile` 이 없는지 본다
- 인라인 정책 가운데 `ggeolmuse-ec2-secrets-read` · `ggeolmuse-db-backup-write` 를 뺀 나머지는 코드에 없다. `aws iam list-role-policies` 로 보고 필요하면 코드로 옮긴다
- RDS 마스터 비밀번호는 코드 밖이다. 새로 만들 일은 스냅숏 복원이라 비밀번호를 코드에 두지 않는다

## 주의사항

- `terraform.tfvars` 파일은 Git에 커밋하지 마세요 (민감한 정보 포함)
- Production 환경에서는 반드시 `terraform plan`으로 변경사항을 확인한 후 apply하세요
- Terraform state 는 S3 `ggeolmuse-tfstate-apne2` 에 있다 (2026-09-08). 어느 기계에서든 `terraform init` 후 바로 쓴다
- `slack_webhook_url` 은 state 에 평문으로 남는다(버킷은 암호화)
- EC2 에 실제로 연결된 인스턴스 프로파일은 콘솔에서 만든 `ggeolmuse-ec2-role` 이다. 이 프로파일과 역할을 가져와 덤프 쓰기 · 시크릿 읽기 정책을 둔다
