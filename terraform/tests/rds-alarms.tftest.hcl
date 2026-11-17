# RDS 알람(CPU · 저장 공간 · 연결 수)과 슬랙 알림을 가짜 AWS 프로바이더로 확인
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

# 슬랙 API Destination 만 다른 ARN(주소별 확인 대상과 구별)
override_resource {
  target = aws_cloudwatch_event_api_destination.slack
  values = { arn = "arn:aws:events:ap-northeast-2:123456789012:api-destination/ggeolmuse-slack/x" }
}

# imports.tf 의 자원마다 override_resource(가짜 프로바이더는 import 를 못 함)
override_resource {
  target = aws_instance.app
}
override_resource {
  target = aws_security_group.app
}
override_resource {
  target = aws_db_instance.postgres
}
override_resource {
  target = aws_iam_role.node
}
override_resource {
  target = aws_iam_instance_profile.node
}
override_resource {
  target = aws_iam_role_policy_attachments_exclusive.node
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
  ec2_instance_id                = "i-0123456789abcdef0"
  slack_webhook_url              = "https://hooks.slack.com/services/T000/B000/XXXX"
  ec2_ami_id                     = "ami-0123456789abcdef0"
  ec2_subnet_id                  = "subnet-0123456789abcdef0"
  ec2_key_name                   = null
  ec2_root_volume_size           = 30
  ec2_root_volume_type           = "gp3"
  vpc_id                         = "vpc-0123456789abcdef0"
  ec2_security_group_id          = "sg-0123456789abcdef0"
  ec2_security_group_description = "ggeolmuse k3s node"
  ec2_extra_ingress              = []
  node_role_policy_arns          = []
  rds_instance_id                = "ggeolmuse-postgres"
  rds_allocated_storage          = 20
  rds_storage_type               = "gp2"
  rds_storage_encrypted          = true
  rds_publicly_accessible        = false
  rds_subnet_group_name          = "default-vpc-0123456789abcdef0"
  rds_security_group_ids         = ["sg-0fedcba9876543210"]
  rds_parameter_group_name       = "ggeolmuse-pg15"
}

run "cpu_storage_connections_alarms" {
  command = plan

  assert {
    condition     = toset(keys(aws_cloudwatch_metric_alarm.rds)) == toset(["cpu", "storage", "connections"])
    error_message = "CPU · 저장 공간 · 연결 수 알람 셋"
  }

  assert {
    condition = (
      aws_cloudwatch_metric_alarm.rds["cpu"].metric_name == "CPUUtilization" &&
      aws_cloudwatch_metric_alarm.rds["cpu"].statistic == "Average" &&
      aws_cloudwatch_metric_alarm.rds["cpu"].comparison_operator == "GreaterThanThreshold" &&
      aws_cloudwatch_metric_alarm.rds["cpu"].threshold == 80 &&
      aws_cloudwatch_metric_alarm.rds["cpu"].evaluation_periods == 3
    )
    error_message = "CPU 는 5분 평균 80% 초과가 15분"
  }

  assert {
    condition = (
      aws_cloudwatch_metric_alarm.rds["storage"].metric_name == "FreeStorageSpace" &&
      aws_cloudwatch_metric_alarm.rds["storage"].statistic == "Minimum" &&
      aws_cloudwatch_metric_alarm.rds["storage"].comparison_operator == "LessThanThreshold" &&
      aws_cloudwatch_metric_alarm.rds["storage"].threshold == 2147483648
    )
    error_message = "남은 저장 공간은 최솟값 2GiB 미만"
  }

  assert {
    condition = (
      aws_cloudwatch_metric_alarm.rds["connections"].metric_name == "DatabaseConnections" &&
      aws_cloudwatch_metric_alarm.rds["connections"].statistic == "Maximum" &&
      aws_cloudwatch_metric_alarm.rds["connections"].comparison_operator == "GreaterThanThreshold" &&
      aws_cloudwatch_metric_alarm.rds["connections"].threshold == 70 &&
      aws_cloudwatch_metric_alarm.rds["connections"].evaluation_periods == 2
    )
    error_message = "연결 수는 5분 최댓값 70 초과가 10분"
  }

  assert {
    condition = alltrue([
      for a in aws_cloudwatch_metric_alarm.rds :
      a.namespace == "AWS/RDS" && a.dimensions["DBInstanceIdentifier"] == "ggeolmuse-postgres" && a.period == 300
    ])
    error_message = "알람은 이 RDS 인스턴스의 5분 지표"
  }

  assert {
    condition     = alltrue([for a in aws_cloudwatch_metric_alarm.rds : a.treat_missing_data == "notBreaching"])
    error_message = "RDS 가 꺼진 밤 · 주말은 지표가 없어 알람 아님"
  }

  assert {
    condition     = alltrue([for a in aws_cloudwatch_metric_alarm.rds : length(a.alarm_actions) == 1])
    error_message = "알람은 SNS 주제 하나로(이메일)"
  }
}

run "alarm_state_changes_to_slack" {
  command = apply

  # 지우기 금지 운영 자원은 만들지 않음(정리 단계에서 막힘)
  plan_options {
    target = [
      aws_cloudwatch_event_target.rds_alarm_notice,
      aws_cloudwatch_metric_alarm.rds,
    ]
  }

  assert {
    condition     = toset(keys(aws_cloudwatch_event_rule.rds_alarm_notice)) == toset(["alarm", "ok"])
    error_message = "알람 · 복구 두 알림"
  }

  assert {
    condition = (
      toset(jsondecode(aws_cloudwatch_event_rule.rds_alarm_notice["alarm"].event_pattern).detail.alarmName) ==
      toset(["ggeolmuse-rds-cpu", "ggeolmuse-rds-storage", "ggeolmuse-rds-connections"])
    )
    error_message = "슬랙 알림은 RDS 알람 셋의 상태 변화만"
  }

  assert {
    condition     = jsondecode(aws_cloudwatch_event_rule.rds_alarm_notice["ok"].event_pattern).detail.previousState.value == ["ALARM"]
    error_message = "복구 알림은 ALARM 에서 OK 로 갈 때만"
  }

  assert {
    condition     = alltrue([for t in aws_cloudwatch_event_target.rds_alarm_notice : t.arn == "arn:aws:events:ap-northeast-2:123456789012:api-destination/ggeolmuse-slack/x"])
    error_message = "알림은 슬랙 API Destination 으로"
  }

  assert {
    condition     = jsondecode(aws_cloudwatch_event_target.rds_alarm_notice["alarm"].input_transformer[0].input_template).text == ":rotating_light: <desc>"
    error_message = "알람 문구는 알람 설명 그대로"
  }
}

run "no_slack_only_email" {
  command = plan

  variables {
    slack_webhook_url = ""
  }

  assert {
    condition     = length(aws_cloudwatch_metric_alarm.rds) == 3 && length(aws_cloudwatch_event_rule.rds_alarm_notice) == 0
    error_message = "웹훅이 없으면 알람은 이메일로만"
  }
}

run "no_rds_no_alarms" {
  command = plan

  variables {
    rds_instance_id = ""
  }

  assert {
    condition     = length(aws_cloudwatch_metric_alarm.rds) == 0 && length(aws_cloudwatch_event_rule.rds_alarm_notice) == 0
    error_message = "rds_instance_id 가 비면 RDS 알람 없음"
  }
}
