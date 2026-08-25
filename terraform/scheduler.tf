# EC2 자동 시작/중지 스케줄러 (EventBridge Scheduler)
# 한국 시간(Asia/Seoul) 기준 월~금 07:00 시작 / 19:00 중지
# Lambda 없이 universal target으로 EC2 API를 직접 호출한다.

locals {
  ec2_instance_arn = "arn:aws:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:instance/${var.ec2_instance_id}"
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
        Resource = local.ec2_instance_arn
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
  state      = var.scheduler_enabled ? "ENABLED" : "DISABLED"

  schedule_expression          = "cron(${var.scheduler_start_minute} ${var.scheduler_start_hour} ? * ${var.scheduler_days} *)"
  schedule_expression_timezone = var.scheduler_timezone

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:ec2:startInstances"
    role_arn = aws_iam_role.ec2_scheduler.arn

    input = jsonencode({
      InstanceIds = [var.ec2_instance_id]
    })

    retry_policy {
      maximum_retry_attempts       = 3
      maximum_event_age_in_seconds = 300
    }
  }

  # 스케줄이 정책보다 먼저 만들어지지 않게 한다.
  # 참조가 없어서 terraform 이 둘 사이의 순서를 모른다.
  depends_on = [aws_iam_role_policy.ec2_scheduler]
}

resource "aws_scheduler_schedule" "ec2_stop" {
  name       = "${var.project_name}-ec2-stop"
  group_name = aws_scheduler_schedule_group.ec2_scheduler.name
  state      = var.scheduler_enabled ? "ENABLED" : "DISABLED"

  schedule_expression          = "cron(${var.scheduler_stop_minute} ${var.scheduler_stop_hour} ? * ${var.scheduler_days} *)"
  schedule_expression_timezone = var.scheduler_timezone

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:ec2:stopInstances"
    role_arn = aws_iam_role.ec2_scheduler.arn

    input = jsonencode({
      InstanceIds = [var.ec2_instance_id]
    })

    retry_policy {
      maximum_retry_attempts       = 3
      maximum_event_age_in_seconds = 300
    }
  }

  # 스케줄이 정책보다 먼저 만들어지지 않게 한다.
  # 참조가 없어서 terraform 이 둘 사이의 순서를 모른다.
  depends_on = [aws_iam_role_policy.ec2_scheduler]
}
