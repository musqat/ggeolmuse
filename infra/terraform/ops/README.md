# Terraform

운영 AWS 자원(RDS · 스케줄러 · 알람 · 백업 버킷 · 상태 버킷)을 Terraform 으로 관리한다. 콘솔에서 먼저 만든 자원은 import 블록으로
상태에 넣었고, 상태는 S3 `ggeolmuse-tfstate-apne2` 에 둔다. AWS 제공자는 잠금 파일로 5.100.0 에 고정한다(linux · windows · macOS 해시).

## 관리되는 리소스

- 운영 핵심 자원 (`core.tf`, 가져오는 블록은 `imports.tf`)
  - RDS `rds_instance_id`(PostgreSQL 15 · db.t4g.micro · Single-AZ, 저장 공간 자동 늘리기 1000GB 까지 · Performance Insights, 자동 백업 7일 · 창 08:10 ~ 08:40 KST · 삭제 보호 · 지울 때 최종 스냅숏)
  - 상태 버킷 `ggeolmuse-tfstate-apne2`(버전 관리 · SSE-S3 · 퍼블릭 차단)
  - 모두 `prevent_destroy` 라 plan 이 교체 · 삭제를 잡으면 오류로 멈춘다
- EventBridge 스케줄러: k8s 노드 셋(`scheduled_instances`) / RDS 평일 시작·중지 (`scheduler.tf`, `rds-scheduler.tf`)
- CloudWatch 알람 + SNS 이메일: 스케줄 실패 · 노드마다 상태 검사 실패 (`monitoring.tf`)
- RDS CloudWatch 알람: CPU 5분 평균 80% 초과 15분 · 남은 저장 공간 2GiB 미만 · 연결 수 5분 최댓값 70 초과 10분 (`rds-alarms.tf`).
  SNS 이메일로 오고, `slack_webhook_url` 이 있으면 슬랙에도 알람 · 복구 한 줄씩. RDS 가 꺼진 밤 · 주말은 지표가 없어 알람이
  아니다. 기준은 `rds_alarm_cpu_percent` · `rds_alarm_free_storage_gib` · `rds_alarm_connections`
- EventBridge 규칙 + API Destination: 노드가 멈추면 슬랙에 `노드 중지 · <노드 이름>` 한 줄, 노드마다 규칙 하나 (`ec2-stop-alert.tf`). `slack_webhook_url` 이 비면
  만들지 않고, 슬랙에 못 보내면 위 SNS 알람으로 온다
- S3 버킷 `ggeolmuse-db-backup-apne2` + 쓰기 정책: 월간 pg_dump 보관 (`db-backup.tf`, 쓰는 역할은 `db_backup_writer_roles` 의 k8s 노드 역할).
  CronJob 쪽은 `infra/k8s/charts/ggeolmuse` 의 `dbBackup`. 버전 관리를 켜 같은 키로 덮어써도 옛 덤프가 30일 남는다
- EventBridge Scheduler(그룹 `ggeolmuse-uptime`) + 규칙 + API Destination: 가동 시간(노드 시작 20분 뒤부터 중지 전 정시까지)에
  5분마다 프론트 · 시세 API · /auth 를 AWS 에서 GET 으로 확인 (`uptime-check.tf`). 응답 본문은 보지 않고, 2xx 가 아니거나 시간 초과면
  주소별 CloudWatch 알람이 ALARM 이 되고 슬랙에 실패 · 복구 한 줄씩, SNS 이메일(`alarm_email`)로도 온다. 알람은 확인이 있었던 5분 구간만
  세어 노드가 꺼진 시간에는 상태가 그대로고 알림도 없다(실패한 채 꺼지면 다음 날 첫 확인이 성공할 때 복구 한 줄). 일정이 이벤트를 넣지
  못하면 그룹 알람이 SNS 로 온다. 주소는 `uptime_check_urls`, `slack_webhook_url` 이 비면 만들지 않는다

k8s 클러스터(노드 셋 · 보안 그룹 · 노드 역할)는 이 루트에 없다. 상태를 따로 둔 [`infra/terraform/cluster`](../cluster) 루트에 있다([infra/k8s/README.md](../../k8s/README.md)).

## PR 의 읽기 전용 plan

`infra/terraform/ops/` 을 바꾸는 PR 에서 `.github/workflows/terraform-plan.yml` 이 실제 상태로 plan 을 돌려 가져오기 · 만들기 · 바꾸기 ·
지우기 대상 주소만 잡 요약에 남긴다. 값은 로그에 남기지 않고 상태 잠금은 쓰지 않는다. 역할(`ci-plan-role.tf`)은 GitHub OIDC 로 이 저장소의
`pull_request` 실행만 맡고, 이 루트가 쓰는 서비스의 읽기와 상태 파일 읽기만 갖는다. 문법 검사와 가짜 AWS 프로바이더 테스트(`tests/*.tftest.hcl`)는
CI 가 돌린다.
