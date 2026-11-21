# 평일 스케줄 켜고 끄기를 가짜 AWS 프로바이더로 확인
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
