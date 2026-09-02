# RDS 자동 시작/중지 스케줄러 (EventBridge Scheduler)
# EC2 와 같은 방식이되 시각을 어긋나게 둔다.
#   시작  RDS 06:50 -> EC2 07:00   기동에 5~10분 걸려 먼저 켠다
#   중지  EC2 19:00 -> RDS 19:05   앱이 끊긴 뒤에 내린다
#
# rds_instance_id 값이 없으면 아무것도 만들지 않는다.

locals {
  rds_enabled  = var.rds_instance_id != ""
  rds_arn      = "arn:aws:rds:${var.aws_region}:${data.aws_caller_identity.current.account_id}:db:${var.rds_instance_id}"
  rds_resource = local.rds_enabled ? 1 : 0
}

resource "aws_iam_role" "rds_scheduler" {
  count       = local.rds_resource
  name        = "${var.project_name}-rds-scheduler-role"
  description = "Role for EventBridge Scheduler to start/stop RDS"

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
    Name        = "${var.project_name}-rds-scheduler-role"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# 대상 인스턴스에 한정한 최소 권한
resource "aws_iam_role_policy" "rds_scheduler" {
  count = local.rds_resource
  name  = "${var.project_name}-rds-scheduler-policy"
  role  = aws_iam_role.rds_scheduler[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "rds:StartDBInstance",
          "rds:StopDBInstance"
        ]
        Resource = local.rds_arn
      },
      {
        # 스케줄러가 현재 상태를 확인한다. Describe 는 리소스 한정이 안 된다.
        Effect   = "Allow"
        Action   = "rds:DescribeDBInstances"
        Resource = "*"
      }
    ]
  })
}

resource "aws_scheduler_schedule_group" "rds_scheduler" {
  count = local.rds_resource
  name  = "${var.project_name}-rds-scheduler"

  tags = {
    Name        = "${var.project_name}-rds-scheduler"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_scheduler_schedule" "rds_start" {
  count      = local.rds_resource
  name       = "${var.project_name}-rds-start"
  group_name = aws_scheduler_schedule_group.rds_scheduler[0].name
  state      = var.scheduler_enabled ? "ENABLED" : "DISABLED"

  schedule_expression          = "cron(${var.rds_start_minute} ${var.rds_start_hour} ? * ${var.scheduler_days} *)"
  schedule_expression_timezone = var.scheduler_timezone

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:rds:startDBInstance"
    role_arn = aws_iam_role.rds_scheduler[0].arn

    input = jsonencode({
      DbInstanceIdentifier = var.rds_instance_id
    })

    retry_policy {
      maximum_retry_attempts       = 3
      maximum_event_age_in_seconds = 300
    }
  }

  depends_on = [aws_iam_role_policy.rds_scheduler]
}

resource "aws_scheduler_schedule" "rds_stop" {
  count      = local.rds_resource
  name       = "${var.project_name}-rds-stop"
  group_name = aws_scheduler_schedule_group.rds_scheduler[0].name
  state      = var.scheduler_enabled ? "ENABLED" : "DISABLED"

  schedule_expression          = "cron(${var.rds_stop_minute} ${var.rds_stop_hour} ? * ${var.scheduler_days} *)"
  schedule_expression_timezone = var.scheduler_timezone

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:rds:stopDBInstance"
    role_arn = aws_iam_role.rds_scheduler[0].arn

    input = jsonencode({
      DbInstanceIdentifier = var.rds_instance_id
    })

    retry_policy {
      maximum_retry_attempts       = 3
      maximum_event_age_in_seconds = 300
    }
  }

  depends_on = [aws_iam_role_policy.rds_scheduler]
}
