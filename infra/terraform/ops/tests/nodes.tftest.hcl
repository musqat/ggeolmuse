# 평일 스케줄 켜고 끄기 · 켜고 끌 노드 · 백업 쓰기 역할을 가짜 AWS 프로바이더로 확인
mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = { account_id = "123456789012" }
  }
  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::123456789012:role/mock" }
  }
  mock_resource "aws_sns_topic" {
    defaults = { arn = "arn:aws:sns:ap-northeast-2:123456789012:mock" }
  }
  mock_resource "aws_cloudwatch_event_connection" {
    defaults = { arn = "arn:aws:events:ap-northeast-2:123456789012:connection/mock/mock" }
  }
  mock_resource "aws_cloudwatch_event_api_destination" {
    defaults = { arn = "arn:aws:events:ap-northeast-2:123456789012:api-destination/mock/mock" }
  }
}

# 중지 이벤트 로그 그룹 ARN 을 plan 때 아는 값으로
override_resource {
  target = aws_cloudwatch_log_group.stop_alerts
  values = { arn = "arn:aws:logs:ap-northeast-2:123456789012:log-group:/aws/events/ggeolmuse-stop-alerts" }
}

# 슬랙 API Destination 만 다른 ARN(주소별 확인 대상과 구별)
override_resource {
  target = aws_cloudwatch_event_api_destination.slack
  values = { arn = "arn:aws:events:ap-northeast-2:123456789012:api-destination/ggeolmuse-slack/x" }
}

# imports.tf 의 자원마다 override_resource(가짜 프로바이더는 import 를 못 함)
override_resource {
  target = aws_db_instance.postgres
}
override_resource {
  target = aws_s3_bucket.tfstate
}
override_resource {
  target = aws_s3_bucket_versioning.tfstate
}
override_resource {
  target = aws_s3_bucket_server_side_encryption_configuration.tfstate
}
override_resource {
  target = aws_s3_bucket_public_access_block.tfstate
}

variables {
  scheduled_instances = {
    control-plane = "i-0aaaaaaaaaaaaaaa1"
    worker-a      = "i-0bbbbbbbbbbbbbbb2"
    worker-b      = "i-0ccccccccccccccc3"
  }
  slack_webhook_url        = "https://hooks.slack.com/services/T000/B000/XXXX"
  rds_instance_id          = "ggeolmuse-postgres"
  rds_allocated_storage    = 20
  rds_storage_type         = "gp2"
  rds_storage_encrypted    = true
  rds_publicly_accessible  = false
  rds_subnet_group_name    = "default-vpc-0123456789abcdef0"
  rds_security_group_ids   = ["sg-0fedcba9876543210"]
  rds_parameter_group_name = "ggeolmuse-pg15"
}

run "scheduler_enabled_turns_all_off" {
  command = plan

  variables {
    scheduler_enabled = false
  }

  assert {
    condition = alltrue([for s in concat(values(aws_scheduler_schedule.ec2_start), values(aws_scheduler_schedule.ec2_stop),
    aws_scheduler_schedule.rds_start, aws_scheduler_schedule.rds_stop, values(aws_scheduler_schedule.uptime)) : s.state == "DISABLED"])
    error_message = "scheduler_enabled 를 끄면 EC2 · RDS · 가동 확인 스케줄이 모두 꺼짐"
  }
}

run "ec2_off_keeps_rds" {
  command = plan

  variables {
    ec2_scheduler_enabled = false
  }

  assert {
    condition     = alltrue([for s in concat(values(aws_scheduler_schedule.ec2_start), values(aws_scheduler_schedule.ec2_stop)) : s.state == "DISABLED"])
    error_message = "ec2_scheduler_enabled 를 끄면 EC2 시작 · 중지가 꺼짐"
  }
  assert {
    condition     = alltrue([for s in aws_scheduler_schedule.uptime : s.state == "DISABLED"])
    error_message = "EC2 를 켜지 않으면 가동 확인도 꺼짐"
  }
  assert {
    condition     = aws_scheduler_schedule.rds_start[0].state == "ENABLED" && aws_scheduler_schedule.rds_stop[0].state == "ENABLED"
    error_message = "EC2 만 끄면 RDS 시작 · 중지는 그대로"
  }
}

run "rds_off_keeps_ec2" {
  command = plan

  variables {
    rds_scheduler_enabled = false
  }

  assert {
    condition     = aws_scheduler_schedule.rds_start[0].state == "DISABLED" && aws_scheduler_schedule.rds_stop[0].state == "DISABLED"
    error_message = "rds_scheduler_enabled 를 끄면 RDS 시작 · 중지가 꺼짐"
  }
  assert {
    condition     = alltrue([for s in concat(values(aws_scheduler_schedule.ec2_start), values(aws_scheduler_schedule.uptime)) : s.state == "ENABLED"])
    error_message = "RDS 만 끄면 EC2 · 가동 확인은 그대로"
  }
}

run "nodes_required" {
  command = plan

  variables {
    scheduled_instances = {}
  }

  expect_failures = [var.scheduled_instances]
}

run "nodes_scheduled_and_watched" {
  command = plan

  variables {
    scheduled_instances = {
      control-plane = "i-0aaaaaaaaaaaaaaa1"
      worker-medium = "i-0bbbbbbbbbbbbbbb2"
      worker-large  = "i-0ccccccccccccccc3"
    }
  }

  assert {
    condition = (
      toset(keys(aws_scheduler_schedule.ec2_start)) == toset(["control-plane", "worker-medium", "worker-large"]) &&
      jsondecode(aws_scheduler_schedule.ec2_start["worker-large"].target[0].input).InstanceIds == ["i-0ccccccccccccccc3"] &&
      aws_scheduler_schedule.ec2_start["worker-large"].name == "ggeolmuse-ec2-start-worker-large"
    )
    error_message = "평일 시작은 노드마다 스케줄 하나, 그 노드만 켬(한 노드 실패가 다른 노드를 막지 않음)"
  }
  assert {
    condition = (
      toset(keys(aws_scheduler_schedule.ec2_stop)) == toset(["control-plane", "worker-medium", "worker-large"]) &&
      jsondecode(aws_scheduler_schedule.ec2_stop["worker-large"].target[0].input).InstanceIds == ["i-0ccccccccccccccc3"] &&
      aws_scheduler_schedule.ec2_stop["worker-large"].name == "ggeolmuse-ec2-stop-worker-large"
    )
    error_message = "평일 중지는 노드마다 스케줄 하나, 그 노드만 끔"
  }
  assert {
    condition     = alltrue([for s in concat(values(aws_scheduler_schedule.ec2_start), values(aws_scheduler_schedule.ec2_stop)) : s.group_name == "ggeolmuse-ec2-scheduler"])
    error_message = "노드 스케줄은 모두 한 그룹(실패 알람이 그룹 단위)"
  }
  assert {
    condition     = length(jsondecode(aws_iam_role_policy.ec2_scheduler.policy).Statement[0].Resource) == 3 && alltrue([for a in jsondecode(aws_iam_role_policy.ec2_scheduler.policy).Statement[0].Resource : startswith(a, "arn:aws:ec2:ap-northeast-2:123456789012:instance/i-0")])
    error_message = "스케줄 역할은 노드 셋만 켜고 끌 수 있음"
  }
  assert {
    condition     = toset(keys(aws_cloudwatch_event_rule.ec2_stopped)) == toset(["control-plane", "worker-medium", "worker-large"]) && jsondecode(aws_cloudwatch_event_rule.ec2_stopped["worker-large"].event_pattern).detail["instance-id"] == ["i-0ccccccccccccccc3"]
    error_message = "중지 알림 규칙이 노드마다 하나, 그 노드만 봄"
  }
  assert {
    condition     = jsondecode(aws_cloudwatch_event_target.ec2_stopped_slack["worker-large"].input).text == "노드 중지 · worker-large"
    error_message = "중지 알림 문구에 노드 이름, 이모지 없음"
  }
  assert {
    condition     = toset(keys(aws_cloudwatch_metric_alarm.ec2_stop_alert_failed)) == toset(["control-plane", "worker-medium", "worker-large"]) && aws_cloudwatch_metric_alarm.ec2_stop_alert_failed["worker-large"].dimensions.RuleName == "ggeolmuse-ec2-stopped-worker-large"
    error_message = "슬랙 전송 실패 알람이 중지 알림 규칙마다 하나"
  }
  assert {
    condition     = toset(keys(aws_cloudwatch_metric_alarm.ec2_status_check)) == toset(["control-plane", "worker-medium", "worker-large"]) && aws_cloudwatch_metric_alarm.ec2_status_check["worker-large"].alarm_name == "ggeolmuse-ec2-status-check-worker-large" && aws_cloudwatch_metric_alarm.ec2_status_check["worker-large"].dimensions.InstanceId == "i-0ccccccccccccccc3"
    error_message = "상태 알람이 노드마다 하나, 이름에 노드 이름"
  }
}

run "nodes_started_to_slack" {
  command = plan

  variables {
    scheduled_instances = {
      control-plane = "i-0aaaaaaaaaaaaaaa1"
      worker-medium = "i-0bbbbbbbbbbbbbbb2"
      worker-large  = "i-0ccccccccccccccc3"
    }
  }

  assert {
    condition = (
      toset(keys(aws_cloudwatch_event_rule.ec2_started)) == toset(["control-plane", "worker-medium", "worker-large"]) &&
      jsondecode(aws_cloudwatch_event_rule.ec2_started["worker-large"].event_pattern).detail.state == ["running"] &&
      jsondecode(aws_cloudwatch_event_rule.ec2_started["worker-large"].event_pattern).detail["instance-id"] == ["i-0ccccccccccccccc3"]
    )
    error_message = "시작 알림 규칙이 노드마다 하나, 그 노드가 running 이 될 때만"
  }
  assert {
    condition     = jsondecode(aws_cloudwatch_event_target.ec2_started_slack["worker-large"].input).text == "노드 시작 · worker-large"
    error_message = "시작 알림 문구에 노드 이름"
  }
  assert {
    condition     = aws_cloudwatch_metric_alarm.ec2_start_alert_failed["worker-large"].dimensions.RuleName == "ggeolmuse-ec2-started-worker-large"
    error_message = "슬랙 전송 실패 알람이 시작 알림 규칙마다 하나"
  }
}

run "nodes_started_quiet_without_slack" {
  command = plan

  variables {
    slack_webhook_url = ""
  }

  assert {
    condition     = length(aws_cloudwatch_event_rule.ec2_started) == 0 && length(aws_cloudwatch_metric_alarm.ec2_start_alert_failed) == 0
    error_message = "웹훅이 없으면 시작 알림 없음"
  }
  assert {
    condition     = length(aws_cloudwatch_event_rule.ec2_spot_warning) == 0 && length(aws_cloudwatch_metric_alarm.ec2_spot_warning_alert_failed) == 0
    error_message = "웹훅이 없으면 스팟 회수 예고 없음"
  }
}

run "spot_warning_to_slack" {
  command = apply

  plan_options {
    target = [
      aws_cloudwatch_event_target.ec2_spot_warning_slack,
      aws_cloudwatch_event_target.ec2_spot_warning_log,
      aws_cloudwatch_metric_alarm.ec2_spot_warning_alert_failed,
    ]
  }

  assert {
    condition = (
      toset(keys(aws_cloudwatch_event_rule.ec2_spot_warning)) == toset(["control-plane", "worker-a", "worker-b"]) &&
      jsondecode(aws_cloudwatch_event_rule.ec2_spot_warning["worker-a"].event_pattern)["detail-type"] == ["EC2 Spot Instance Interruption Warning"] &&
      jsondecode(aws_cloudwatch_event_rule.ec2_spot_warning["worker-a"].event_pattern).detail["instance-id"] == ["i-0bbbbbbbbbbbbbbb2"]
    )
    error_message = "스팟 회수 예고 규칙이 노드마다 하나, 그 노드의 예고만"
  }
  assert {
    condition     = jsondecode(aws_cloudwatch_event_target.ec2_spot_warning_slack["worker-a"].input).text == "스팟 회수 예고 · worker-a (2분 뒤 중지)"
    error_message = "회수 예고 문구에 노드 이름"
  }
  assert {
    condition     = aws_cloudwatch_event_target.ec2_spot_warning_log["worker-a"].arn == "arn:aws:logs:ap-northeast-2:123456789012:log-group:/aws/events/ggeolmuse-stop-alerts"
    error_message = "회수 예고 원문도 중지 이벤트 로그 그룹에"
  }
  assert {
    condition     = aws_cloudwatch_metric_alarm.ec2_spot_warning_alert_failed["worker-a"].dimensions.RuleName == "ggeolmuse-ec2-spot-warning-worker-a"
    error_message = "슬랙 전송 실패 알람이 회수 예고 규칙마다 하나"
  }
}

run "stop_events_logged" {
  command = apply

  # 지우기 금지 운영 자원은 만들지 않음(정리 단계에서 막힘)
  plan_options {
    target = [
      aws_cloudwatch_event_target.ec2_stopped_log,
      aws_cloudwatch_log_resource_policy.stop_alerts,
    ]
  }

  assert {
    condition     = aws_cloudwatch_log_group.stop_alerts[0].name == "/aws/events/ggeolmuse-stop-alerts" && aws_cloudwatch_log_group.stop_alerts[0].retention_in_days == 30
    error_message = "중지 이벤트 로그 그룹은 /aws/events/ 아래, 30일 보관"
  }
  assert {
    condition     = toset(keys(aws_cloudwatch_event_target.ec2_stopped_log)) == toset(["control-plane", "worker-a", "worker-b"]) && aws_cloudwatch_event_target.ec2_stopped_log["worker-a"].arn == "arn:aws:logs:ap-northeast-2:123456789012:log-group:/aws/events/ggeolmuse-stop-alerts"
    error_message = "노드 중지 규칙마다 슬랙과 함께 로그 그룹에도 남김"
  }
  assert {
    condition = (
      jsondecode(aws_cloudwatch_log_resource_policy.stop_alerts[0].policy_document).Statement[0].Principal.Service == "events.amazonaws.com" &&
      jsondecode(aws_cloudwatch_log_resource_policy.stop_alerts[0].policy_document).Statement[0].Resource == "arn:aws:logs:ap-northeast-2:123456789012:log-group:/aws/events/ggeolmuse-stop-alerts:*"
    )
    error_message = "EventBridge 만 이 로그 그룹에 씀"
  }
}

run "backup_writer_roles" {
  command = plan

  variables {
    db_backup_writer_roles = ["ggeolmuse-k8s-node"]
  }

  assert {
    condition     = keys(aws_iam_role_policy.db_backup_writer) == ["ggeolmuse-k8s-node"] && aws_iam_role_policy.db_backup_writer["ggeolmuse-k8s-node"].role == "ggeolmuse-k8s-node" && aws_iam_role_policy.db_backup_writer["ggeolmuse-k8s-node"].name == "ggeolmuse-db-backup-write"
    error_message = "더한 역할마다 백업 쓰기 정책 하나"
  }
}

run "schedule_failures_to_slack" {
  command = apply

  # 지우기 금지 운영 자원은 만들지 않음(정리 단계에서 막힘)
  plan_options {
    target = [
      aws_cloudwatch_event_target.schedule_alarm_notice,
      aws_cloudwatch_metric_alarm.schedule_alarm_notice_failed,
    ]
  }

  assert {
    condition = toset(jsondecode(aws_cloudwatch_event_rule.schedule_alarm_notice[0].event_pattern).detail.alarmName) == toset([
      "ggeolmuse-scheduler-target-error", "ggeolmuse-scheduler-invocation-dropped",
      "ggeolmuse-rds-scheduler-target-error", "ggeolmuse-rds-scheduler-invocation-dropped",
      "ggeolmuse-uptime-schedule-target-error", "ggeolmuse-uptime-schedule-invocation-dropped",
    ])
    error_message = "EC2 · RDS · 사이트 확인 스케줄의 실패 알람 여섯을 슬랙 규칙 하나가 봄"
  }
  assert {
    condition     = jsondecode(aws_cloudwatch_event_rule.schedule_alarm_notice[0].event_pattern).detail.state.value == ["ALARM"]
    error_message = "알람이 될 때만 보냄"
  }
  assert {
    condition = (
      aws_cloudwatch_event_target.schedule_alarm_notice[0].arn == "arn:aws:events:ap-northeast-2:123456789012:api-destination/ggeolmuse-slack/x" &&
      jsondecode(aws_cloudwatch_event_target.schedule_alarm_notice[0].input_transformer[0].input_template).text == "스케줄 실패 · <desc>"
    )
    error_message = "슬랙에 알람 설명 한 줄"
  }
  assert {
    condition     = aws_cloudwatch_metric_alarm.schedule_alarm_notice_failed[0].dimensions.RuleName == "ggeolmuse-schedule-alarm-notice"
    error_message = "슬랙에 못 보내면 이메일 알람"
  }
}

run "schedule_failures_email_only_without_slack" {
  command = plan

  variables {
    slack_webhook_url = ""
  }

  assert {
    condition     = length(aws_cloudwatch_event_rule.schedule_alarm_notice) == 0 && length(aws_cloudwatch_metric_alarm.schedule_alarm_notice_failed) == 0
    error_message = "웹훅이 없으면 슬랙 규칙 없음(이메일만)"
  }
}
