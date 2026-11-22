# EC2 평일 07:00 시작 · 19:00 중지(KST, EventBridge Scheduler 가 Lambda 없이 EC2 API 호출)

locals {
  # 평일 켜고 끄는 EC2(이름 → 인스턴스 id)
  scheduled_instances = var.scheduled_instances
  ec2_instance_arns   = [for id in values(local.scheduled_instances) : "arn:aws:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:instance/${id}"]
  # EC2 평일 시작 · 중지와 가동 확인 스케줄 상태
  ec2_schedule_state = coalesce(var.ec2_scheduler_enabled, var.scheduler_enabled) ? "ENABLED" : "DISABLED"
}

# Scheduler가 EC2를 제어할 때 사용할 역할
resource "aws_iam_role" "ec2_scheduler" {
  name        = "${var.project_name}-ec2-scheduler-role"
  description = "Role for EventBridge Scheduler to start/stop EC2"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "scheduler.amazonaws.com"
        }
        Action = "sts:AssumeRole"
        Condition = {
          StringEquals = {
            "aws:SourceAccount" = data.aws_caller_identity.current.account_id
          }
        }
      }
    ]
  })

  tags = {
    Name        = "${var.project_name}-ec2-scheduler-role"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# 대상 인스턴스에 한정한 최소 권한
resource "aws_iam_role_policy" "ec2_scheduler" {
  name = "${var.project_name}-ec2-scheduler-policy"
  role = aws_iam_role.ec2_scheduler.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ec2:StartInstances",
          "ec2:StopInstances"
        ]
        Resource = local.ec2_instance_arns
      }
    ]
  })
}

resource "aws_scheduler_schedule_group" "ec2_scheduler" {
  name = "${var.project_name}-ec2-scheduler"

  tags = {
    Name        = "${var.project_name}-ec2-scheduler"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_scheduler_schedule" "ec2_start" {
  name       = "${var.project_name}-ec2-start"
  group_name = aws_scheduler_schedule_group.ec2_scheduler.name
  state      = local.ec2_schedule_state

  schedule_expression          = "cron(${var.scheduler_start_minute} ${var.scheduler_start_hour} ? * ${var.scheduler_days} *)"
  schedule_expression_timezone = var.scheduler_timezone

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:ec2:startInstances"
    role_arn = aws_iam_role.ec2_scheduler.arn

    input = jsonencode({
      InstanceIds = values(local.scheduled_instances)
    })

    retry_policy {
      maximum_retry_attempts       = 3
      maximum_event_age_in_seconds = 300
    }
  }

  # 정책 다음에 스케줄(참조가 없어 terraform 이 순서를 모름)
  depends_on = [aws_iam_role_policy.ec2_scheduler]
}

resource "aws_scheduler_schedule" "ec2_stop" {
  name       = "${var.project_name}-ec2-stop"
  group_name = aws_scheduler_schedule_group.ec2_scheduler.name
  state      = local.ec2_schedule_state

  schedule_expression          = "cron(${var.scheduler_stop_minute} ${var.scheduler_stop_hour} ? * ${var.scheduler_days} *)"
  schedule_expression_timezone = var.scheduler_timezone

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:ec2:stopInstances"
    role_arn = aws_iam_role.ec2_scheduler.arn

    input = jsonencode({
      InstanceIds = values(local.scheduled_instances)
    })

    retry_policy {
      maximum_retry_attempts       = 3
      maximum_event_age_in_seconds = 300
    }
  }

  # 정책 다음에 스케줄(참조가 없어 terraform 이 순서를 모름)
  depends_on = [aws_iam_role_policy.ec2_scheduler]
}
