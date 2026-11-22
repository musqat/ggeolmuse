# Ggeolmuse Terraform Infrastructure

이 디렉토리는 Ggeolmuse 프로젝트의 AWS 인프라를 Terraform으로 관리합니다.

## 사전 요구사항

- Terraform >= 1.10 (backend 의 `use_lockfile` 때문)
- AWS CLI 설정 완료
- 적절한 AWS IAM 권한

## 사용법

### 1. 노드 셋 인스턴스 ID · 노드 역할 이름 확인

```bash
cd ../k8s/terraform && terraform output node_ids && terraform output node_role_name
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
  - RDS `rds_instance_id`(PostgreSQL 15 · db.t4g.micro · Single-AZ, 저장 공간 자동 늘리기 1000GB 까지 · Performance Insights, 자동 백업 7일 · 창 08:10 ~ 08:40 KST · 삭제 보호 · 지울 때 최종 스냅숏)
  - 상태 버킷 `ggeolmuse-tfstate-apne2`(버전 관리 · SSE-S3 · 퍼블릭 차단)
  - 모두 `prevent_destroy` 라 plan 이 교체 · 삭제를 잡으면 오류로 멈춘다
- EventBridge 스케줄러: EC2 / RDS 평일 시작·중지 (`scheduler.tf`, `rds-scheduler.tf`). EC2 · RDS 를
  따로 끌 수 있고(`ec2_scheduler_enabled` · `rds_scheduler_enabled`), 대상 EC2 는 `scheduled_instances`(k8s 노드 셋, 비면 plan 이 멈춤)
- CloudWatch 알람 + SNS 이메일: 스케줄 실패 감지 (`monitoring.tf`)
- RDS CloudWatch 알람: CPU 5분 평균 80% 초과 15분 · 남은 저장 공간 2GiB 미만 · 연결 수 5분 최댓값 70 초과 10분 (`rds-alarms.tf`).
  SNS 이메일로 오고, `slack_webhook_url` 이 있으면 슬랙에도 알람 · 복구 한 줄씩. RDS 가 꺼진 밤 · 주말은 지표가 없어 알람이
  아니다. 기준은 `rds_alarm_cpu_percent` · `rds_alarm_free_storage_gib` · `rds_alarm_connections`
- EventBridge 규칙 + API Destination: EC2 가 멈추면 슬랙에 `down` 한 줄 (`ec2-stop-alert.tf`). `slack_webhook_url` 이 비면
  만들지 않고, 슬랙에 못 보내면 위 SNS 알람으로 온다
- S3 버킷 `ggeolmuse-db-backup-apne2` + 쓰기 정책: 월간 pg_dump 보관 (`db-backup.tf`, 쓰는 역할은 `db_backup_writer_roles` 의 k8s 노드 역할).
  CronJob 쪽은 `helm/ggeolmuse` 의 `dbBackup`. 버전 관리를 켜 같은 키로 덮어써도 옛 덤프가 30일 남는다
- EventBridge Scheduler(그룹 `ggeolmuse-uptime`) + 규칙 + API Destination: 가동 시간(EC2 시작 20분 뒤부터 중지 전 정시까지)에
  5분마다 프론트 · 시세 API · /auth 를 AWS 에서 GET 으로 확인 (`uptime-check.tf`). 응답 본문은 보지 않고, 2xx 가 아니거나 시간 초과면
  주소별 CloudWatch 알람이 ALARM 이 되고 슬랙에 실패 · 복구 한 줄씩, SNS 이메일(`alarm_email`)로도 온다. 알람은 확인이 있었던 5분 구간만
  세어 EC2 가 꺼진 시간에는 상태가 그대로고 알림도 없다(실패한 채 꺼지면 다음 날 첫 확인이 성공할 때 복구 한 줄). 일정이 이벤트를 넣지
  못하면 그룹 알람이 SNS 로 온다. 주소는 `uptime_check_urls`, `slack_webhook_url` 이 비면 만들지 않는다

k8s 클러스터(노드 셋 · 보안 그룹 · 노드 역할)는 이 루트에 없다. 상태를 따로 둔 [`k8s/terraform`](../k8s/terraform) 루트에 있고, 올리는 순서는 [k8s/README.md](../k8s/README.md).

문법 검사와 가짜 AWS 프로바이더 테스트(`tests/*.tftest.hcl`, AWS 에 닿지 않음)는 아래 한 줄이다. 멀티 노드 쪽 테스트는
[k8s/README.md](../k8s/README.md#파일) 에 있다.

```bash
terraform init -backend=false && terraform validate && terraform test
```

## 운영 자원 가져오기

콘솔에서 만든 RDS · 상태 버킷을 `imports.tf` 의 import 블록으로 상태에 넣는다. 한 번 가져온 자원의 import 블록은 아무 일도 하지
않는다.

1. 아래 표의 값을 `terraform.tfvars` 에 채운다. 교체가 걸린 값은 plan 전에 명령으로 확인한다
2. `terraform plan` 을 본다. 교체가 잡히면 `prevent_destroy` 오류로 멈추므로 표에서 "다르면 교체" 인 값을 다시 본다
3. 바꾸겠다고 나온 값은 실제 값을 tfvars(변수가 없으면 코드)로 옮기고, 남은 변경이 의도한 것뿐일 때 `terraform apply`
4. apply 는 RDS 가 켜져 있는 평일 06:40 ~ 19:05 KST 에, `aws rds describe-db-instances --db-instance-identifier ggeolmuse-postgres
   --query 'DBInstances[0].DBInstanceStatus'` 가 `available` 일 때 한다. 멈춘 RDS 는 수정이 `InvalidDBInstanceState` 로 거절돼
   apply 가 중간에 실패한다
5. 다시 `terraform plan` 이 `No changes.` 인지 본다

| 변수 | 다르면 | 확인 명령 |
|---|---|---|
| `rds_*` | 암호화 · 사용자 이름은 교체, 나머지는 교체 없이 바뀜 | `aws rds describe-db-instances --db-instance-identifier ggeolmuse-postgres --query 'DBInstances[0].[AllocatedStorage,MaxAllocatedStorage,PerformanceInsightsEnabled,CopyTagsToSnapshot,StorageType,StorageEncrypted,PubliclyAccessible,DBSubnetGroup.DBSubnetGroupName,VpcSecurityGroups[].VpcSecurityGroupId,DBParameterGroups[].DBParameterGroupName,BackupRetentionPeriod,PreferredBackupWindow,PreferredMaintenanceWindow,DeletionProtection]'` |

- RDS 보안 그룹의 노드 5432 규칙은 k8s terraform 이 둔다(`rds_security_group_id`)
- RDS 마스터 비밀번호는 코드 밖이다. 새로 만들 일은 스냅숏 복원이라 비밀번호를 코드에 두지 않는다
- 유지 보수 창(`PreferredMaintenanceWindow`)이 백업 창 `23:10-23:40`(UTC)과 겹치면 백업 창 수정이 거절된다. 겹치면
  `rds_backup_window` 를 RDS 가동 시간 안의 겹치지 않는 30분으로 바꾼다

## 백업 · 복원

| | RDS 자동 백업 | 월간 덤프 |
|---|---|---|
| 위치 | RDS 안(시점 복원) | S3 `ggeolmuse-db-backup-apne2/YYYY/MM/<DB>-<시각>.dump` |
| 주기 · 보존 | 하루 한 번 스냅숏(창 08:10 ~ 08:40 KST, 꺼진 날은 없음) + 트랜잭션 로그, 7일 | 매달 1일 09:00 KST, 400일 |
| 쓰는 때 | 잘못된 마이그레이션 · 데이터 손상(최근 7일) | RDS 자체를 잃었을 때 · 7일보다 오래된 시점 |

### 시점 복원

새 인스턴스로 복원한 뒤 이름을 바꿔 끼운다. 엔드포인트가 식별자로 정해져 시크릿의 `DB_URL_*` 는 그대로 쓴다.

1. 복원 시각 · 파라미터 그룹 확인 — `aws rds describe-db-instances --db-instance-identifier ggeolmuse-postgres
   --query 'DBInstances[0].[LatestRestorableTime,DBParameterGroups[].DBParameterGroupName]'`
2. 새 인스턴스 — 서브넷 그룹 · 보안 그룹 · 파라미터 그룹은 tfvars 의 `rds_subnet_group_name` · `rds_security_group_ids` ·
   `rds_parameter_group_name` 과 같게. 파라미터 그룹을 빼면 엔진 기본 그룹으로 뜬다

   ```bash
   aws rds restore-db-instance-to-point-in-time --source-db-instance-identifier ggeolmuse-postgres \
     --target-db-instance-identifier ggeolmuse-postgres-restore --restore-time <UTC 시각> \
     --db-subnet-group-name <그룹> --vpc-security-group-ids <SG> --db-parameter-group-name <파라미터 그룹> \
     --no-publicly-accessible --deletion-protection
   ```

3. 새 인스턴스가 `available` 이 되면 클러스터 안 임시 파드에서 새 엔드포인트로 접속해 주요 테이블의 행 수를 본다
4. 노드 셋을 내려 앱 쓰기를 멈추고 이름을 바꾼다: 옛 것 `ggeolmuse-postgres` → `ggeolmuse-postgres-old`, 새 것 → `ggeolmuse-postgres`
   (`aws rds modify-db-instance --new-db-instance-identifier … --apply-immediately`)
5. terraform 상태의 RDS 를 새 것으로: `terraform state rm 'aws_db_instance.postgres[0]'` 뒤 plan 이면 `imports.tf` 가 새 인스턴스를 가져온다
6. 노드 셋을 올려 화면을 보고, 며칠 뒤 옛 인스턴스의 삭제 보호를 끄고 지운다

최종 스냅숏에서 되살릴 때는 2번을 `aws rds restore-db-instance-from-db-snapshot --db-snapshot-identifier ggeolmuse-postgres-final …` 로 바꾼다.

### 덤프 복원 테스트

덤프가 실제로 복원되는지와 걸리는 시간을 노트북에서 본다. 덤프 읽기는 노트북 자격 증명만 된다.

```bash
aws s3 cp s3://ggeolmuse-db-backup-apne2/2026/11/ ~/ggeolmuse-dumps/ --recursive   # 리포 밖
cd tools && go run ./cmd/db-check -restore ~/ggeolmuse-dumps/*.dump
```

네트워크 없는 `postgres:15-alpine` 컨테이너에 덤프마다 DB 를 만들어 `pg_restore --exit-on-error` 로 넣고, 테이블마다 덤프의 행 수와
복원한 DB 의 `count(*)` 를 대조한다. 덤프마다 복원 시간과 합계가 나오고 행 수가 불일치한 테이블이 있거나 복원이 실패하면 종료 코드 1 이다.
끝나면 컨테이너는 지운다.

덤프를 RDS 에 되살릴 때는 같은 덤프를 클러스터 안 임시 파드(`postgres:15-alpine`)에서 빈 DB 에 `pg_restore --no-owner --no-privileges` 로
넣는다. 덤프에 소유자 · 권한이 없으므로 넣은 뒤 서비스 계정(`svc_<서비스>`)에 소유자 · 권한을 다시 준다.

## 제공자 잠금 파일

`.terraform.lock.hcl` 이 AWS 제공자 버전(5.100.0)과 해시를 고정한다. CI 는 `-lockfile=readonly` 로 이 버전 그대로 받는다.
지금 파일에는 linux_amd64 해시만 있다. Windows · macOS 에서 처음 init 하기 전에 한 번 아래를 돌려 해시를 더하고 커밋한다.

```bash
terraform providers lock -platform=linux_amd64 -platform=windows_amd64 -platform=darwin_arm64
```

제공자를 올릴 때는 `terraform init -upgrade` 뒤 같은 명령을 다시 돌린다.

## PR 의 읽기 전용 plan

`terraform/` 을 바꾸는 PR 에서 `.github/workflows/terraform-plan.yml` 이 실제 상태로 plan 을 돌리고, 가져오기 · 만들기 · 바꾸기 ·
지우기 대상 주소만 잡 요약에 남긴다. 값은 공개 로그에 남지 않게 빼고, 상태 잠금은 쓰지 않는다(`-lock=false`).

역할은 `ci-plan-role.tf` 에 있다. GitHub OIDC 로 이 저장소의 `pull_request` 실행만 맡을 수 있고, 권한은 이 루트가 쓰는 서비스의
Describe · Get · List(계정 전체 자원 대상)와 상태 파일 하나의 읽기다. 시크릿 · 파라미터 · 덤프 버킷 객체는 읽지 못하지만 상태 파일(슬랙 웹훅 평문 포함)은
읽는다. PR 실행은 PR 브랜치의 워크플로 파일로 돌아, 브랜치를 올릴 수 있는 사람은 워크플로를 고쳐 상태 파일과 `TERRAFORM_TFVARS` 를
출력할 수 있다. 브랜치 쓰기 권한이 소유자뿐인 저장소를 전제로 한다.

켜는 순서

1. `terraform.tfvars` 에 `ci_plan_role_enabled = true` 를 넣고 apply 한다. 계정에 GitHub OIDC 공급자가 이미 있으면
   `github_oidc_provider_arn` 에 공급자 ARN 을 넣는다(계정마다 하나)
2. 저장소 시크릿 둘을 넣는다. `TERRAFORM_PLAN_ROLE_ARN` 은 출력 `ci_plan_role_arn`, `TERRAFORM_TFVARS` 는 `terraform.tfvars` 내용 전체에
   `TF_VAR_` 로 넣던 값(`slack_webhook_url = "..."`)을 더한 것. 웹훅이 빠지면 잡 요약에 종료 알림(`ec2-stop-alert.tf`) · 사이트 확인(`uptime-check.tf`)
   자원이 지우기로 나온다
3. `terraform/` 을 고친 PR 에서 `terraform plan` 잡 요약을 본다

시크릿이 없으면(포크 PR 포함) 잡은 안내 한 줄만 남기고 끝난다.

## 주의사항

- `terraform.tfvars` 파일은 Git에 커밋하지 마세요 (민감한 정보 포함)
- Production 환경에서는 반드시 `terraform plan`으로 변경사항을 확인한 후 apply하세요
- Terraform state 는 S3 `ggeolmuse-tfstate-apne2` 에 있다 (2026-09-08). 어느 기계에서든 `terraform init` 후 바로 쓴다
- `slack_webhook_url` 은 state 에 평문으로 남는다(버킷은 암호화)
