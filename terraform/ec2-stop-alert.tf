# EC2 종료 알림. EventBridge 규칙이 API Destination 으로 슬랙 웹훅을 직접 호출
# 웹훅 URL 은 state 에 평문으로 남음

locals {
  stop_alert_enabled = var.slack_webhook_url != ""
}

# API Destination 연결은 인증 방식이 필수라 쓰지 않는 헤더
resource "aws_cloudwatch_event_connection" "slack" {
  count = local.stop_alert_enabled ? 1 : 0

  name               = "${var.project_name}-slack"
  description        = "Slack incoming webhook"
  authorization_type = "API_KEY"

  auth_parameters {
    api_key {
      key   = "X-Unused"
      value = "unused"
    }
  }
}

resource "aws_cloudwatch_event_api_destination" "slack" {
  count = local.stop_alert_enabled ? 1 : 0

  name                             = "${var.project_name}-slack"
  connection_arn                   = aws_cloudwatch_event_connection.slack[0].arn
  invocation_endpoint              = var.slack_webhook_url
  http_method                      = "POST"
  invocation_rate_limit_per_second = 1
}

resource "aws_cloudwatch_event_rule" "ec2_stopped" {
  count = local.stop_alert_enabled ? 1 : 0

  name        = "${var.project_name}-ec2-stopped"
  description = "EC2 stopped 상태 변경"

  event_pattern = jsonencode({
    source        = ["aws.ec2"]
    "detail-type" = ["EC2 Instance State-change Notification"]
    detail = {
      state         = ["stopped"]
      "instance-id" = [var.ec2_instance_id]
    }
  })

  tags = {
    Name        = "${var.project_name}-ec2-stopped"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# EventBridge 가 API Destination 을 부를 때 쓰는 역할
resource "aws_iam_role" "ec2_stop_alert" {
  count = local.stop_alert_enabled ? 1 : 0

  name        = "${var.project_name}-ec2-stop-alert-role"
  description = "Role for EventBridge to call the Slack API destination"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "events.amazonaws.com"
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
    Name        = "${var.project_name}-ec2-stop-alert-role"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_iam_role_policy" "ec2_stop_alert" {
  count = local.stop_alert_enabled ? 1 : 0

  name = "${var.project_name}-ec2-stop-alert-policy"
  role = aws_iam_role.ec2_stop_alert[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "events:InvokeApiDestination"
        Resource = aws_cloudwatch_event_api_destination.slack[0].arn
      }
    ]
  })
}

resource "aws_cloudwatch_event_target" "ec2_stopped_slack" {
  count = local.stop_alert_enabled ? 1 : 0

  rule      = aws_cloudwatch_event_rule.ec2_stopped[0].name
  target_id = "slack"
  arn       = aws_cloudwatch_event_api_destination.slack[0].arn
  role_arn  = aws_iam_role.ec2_stop_alert[0].arn
  input     = jsonencode({ text = ":zzz: down" })
}

# 재시도를 다 써도 슬랙에 못 보낸 경우
resource "aws_cloudwatch_metric_alarm" "ec2_stop_alert_failed" {
  count = local.stop_alert_enabled ? 1 : 0

  alarm_name        = "${var.project_name}-ec2-stop-alert-failed"
  alarm_description = "EC2 종료 알림을 슬랙에 보내지 못함"

  namespace   = "AWS/Events"
  metric_name = "FailedInvocations"

  dimensions = {
    RuleName = aws_cloudwatch_event_rule.ec2_stopped[0].name
  }

  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alarms.arn]

  tags = {
    Name        = "${var.project_name}-ec2-stop-alert-failed"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}
