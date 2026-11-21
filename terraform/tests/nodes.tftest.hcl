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

run "scheduler_enabled_turns_all_off" {
  command = plan

  variables {
    scheduler_enabled = false
  }

  assert {
    condition = alltrue([for s in concat([aws_scheduler_schedule.ec2_start, aws_scheduler_schedule.ec2_stop],
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
    condition     = aws_scheduler_schedule.ec2_start.state == "DISABLED" && aws_scheduler_schedule.ec2_stop.state == "DISABLED"
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
    condition     = aws_scheduler_schedule.ec2_start.state == "ENABLED" && alltrue([for s in aws_scheduler_schedule.uptime : s.state == "ENABLED"])
    error_message = "RDS 만 끄면 EC2 · 가동 확인은 그대로"
  }
}

run "one_instance_by_default" {
  command = plan

  assert {
    condition     = jsondecode(aws_scheduler_schedule.ec2_start.target[0].input).InstanceIds == ["i-0123456789abcdef0"] && jsondecode(aws_scheduler_schedule.ec2_stop.target[0].input).InstanceIds == ["i-0123456789abcdef0"]
    error_message = "노드를 주지 않으면 ec2_instance_id 하나를 켜고 끔"
  }
  assert {
    condition     = keys(aws_cloudwatch_metric_alarm.ec2_status_check) == ["app"] && aws_cloudwatch_metric_alarm.ec2_status_check["app"].alarm_name == "ggeolmuse-ec2-status-check"
    error_message = "상태 알람은 지금 이름 그대로 하나"
  }
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
    condition     = toset(jsondecode(aws_scheduler_schedule.ec2_start.target[0].input).InstanceIds) == toset(["i-0aaaaaaaaaaaaaaa1", "i-0bbbbbbbbbbbbbbb2", "i-0ccccccccccccccc3"])
    error_message = "평일 시작이 노드 셋을 같이 켬"
  }
  assert {
    condition     = toset(jsondecode(aws_scheduler_schedule.ec2_stop.target[0].input).InstanceIds) == toset(["i-0aaaaaaaaaaaaaaa1", "i-0bbbbbbbbbbbbbbb2", "i-0ccccccccccccccc3"])
    error_message = "평일 중지가 노드 셋을 같이 끔"
  }
  assert {
    condition     = length(jsondecode(aws_iam_role_policy.ec2_scheduler.policy).Statement[0].Resource) == 3 && alltrue([for a in jsondecode(aws_iam_role_policy.ec2_scheduler.policy).Statement[0].Resource : startswith(a, "arn:aws:ec2:ap-northeast-2:123456789012:instance/i-0")])
    error_message = "스케줄 역할은 노드 셋만 켜고 끌 수 있음"
  }
  assert {
    condition     = toset(jsondecode(aws_cloudwatch_event_rule.ec2_stopped[0].event_pattern).detail["instance-id"]) == toset(["i-0aaaaaaaaaaaaaaa1", "i-0bbbbbbbbbbbbbbb2", "i-0ccccccccccccccc3"])
    error_message = "중지 알림이 노드 셋을 봄"
  }
  assert {
    condition     = toset(keys(aws_cloudwatch_metric_alarm.ec2_status_check)) == toset(["control-plane", "worker-medium", "worker-large"]) && aws_cloudwatch_metric_alarm.ec2_status_check["worker-large"].alarm_name == "ggeolmuse-ec2-status-check-worker-large" && aws_cloudwatch_metric_alarm.ec2_status_check["worker-large"].dimensions.InstanceId == "i-0ccccccccccccccc3"
    error_message = "상태 알람이 노드마다 하나, 이름에 노드 이름"
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
