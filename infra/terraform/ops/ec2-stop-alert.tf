# EC2 시작 · 중지 · 스팟 회수 예고 알림(노드마다 EventBridge 규칙이 API Destination 으로 슬랙 웹훅을 직접 호출, 문구에 노드 이름)
# 웹훅 URL 은 state 에 평문으로 남음

locals {
  stop_alert_enabled = var.slack_webhook_url != ""
  # 웹훅이 있는지만 민감 표시를 벗김(for_each 키는 노드 이름)
  stop_alert_nodes = nonsensitive(local.stop_alert_enabled) ? local.scheduled_instances : {}
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
  for_each = local.stop_alert_nodes

  name        = "${var.project_name}-ec2-stopped-${each.key}"
  description = "EC2 stopped 상태 변경 · ${each.key}"

  event_pattern = jsonencode({
    source        = ["aws.ec2"]
    "detail-type" = ["EC2 Instance State-change Notification"]
    detail = {
      state         = ["stopped"]
      "instance-id" = [each.value]
    }
  })

  tags = {
    Name        = "${var.project_name}-ec2-stopped-${each.key}"
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
  for_each = local.stop_alert_nodes

  rule      = aws_cloudwatch_event_rule.ec2_stopped[each.key].name
  target_id = "slack"
  arn       = aws_cloudwatch_event_api_destination.slack[0].arn
  role_arn  = aws_iam_role.ec2_stop_alert[0].arn
  input     = jsonencode({ text = "노드 중지 · ${each.key}" })
}

# 재시도를 다 써도 슬랙에 못 보낸 경우
resource "aws_cloudwatch_metric_alarm" "ec2_stop_alert_failed" {
  for_each = local.stop_alert_nodes

  alarm_name        = "${var.project_name}-ec2-stop-alert-failed-${each.key}"
  alarm_description = "EC2 종료 알림을 슬랙에 보내지 못함 · ${each.key}"

  namespace   = "AWS/Events"
  metric_name = "FailedInvocations"

  dimensions = {
    RuleName = aws_cloudwatch_event_rule.ec2_stopped[each.key].name
  }

  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alarms.arn]

  tags = {
    Name        = "${var.project_name}-ec2-stop-alert-failed-${each.key}"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_cloudwatch_event_rule" "ec2_started" {
  for_each = local.stop_alert_nodes

  name        = "${var.project_name}-ec2-started-${each.key}"
  description = "EC2 running 상태 변경 · ${each.key}"

  event_pattern = jsonencode({
    source        = ["aws.ec2"]
    "detail-type" = ["EC2 Instance State-change Notification"]
    detail = {
      state         = ["running"]
      "instance-id" = [each.value]
    }
  })

  tags = {
    Name        = "${var.project_name}-ec2-started-${each.key}"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_cloudwatch_event_target" "ec2_started_slack" {
  for_each = local.stop_alert_nodes

  rule      = aws_cloudwatch_event_rule.ec2_started[each.key].name
  target_id = "slack"
  arn       = aws_cloudwatch_event_api_destination.slack[0].arn
  role_arn  = aws_iam_role.ec2_stop_alert[0].arn
  input     = jsonencode({ text = "노드 시작 · ${each.key}" })
}

# 재시도를 다 써도 슬랙에 못 보낸 경우
resource "aws_cloudwatch_metric_alarm" "ec2_start_alert_failed" {
  for_each = local.stop_alert_nodes

  alarm_name        = "${var.project_name}-ec2-start-alert-failed-${each.key}"
  alarm_description = "EC2 시작 알림을 슬랙에 보내지 못함 · ${each.key}"

  namespace   = "AWS/Events"
  metric_name = "FailedInvocations"

  dimensions = {
    RuleName = aws_cloudwatch_event_rule.ec2_started[each.key].name
  }

  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alarms.arn]

  tags = {
    Name        = "${var.project_name}-ec2-start-alert-failed-${each.key}"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# 스팟 회수 2분 전 예고(온디맨드 노드는 오지 않음)
resource "aws_cloudwatch_event_rule" "ec2_spot_warning" {
  for_each = local.stop_alert_nodes

  name        = "${var.project_name}-ec2-spot-warning-${each.key}"
  description = "EC2 스팟 회수 예고 · ${each.key}"

  event_pattern = jsonencode({
    source        = ["aws.ec2"]
    "detail-type" = ["EC2 Spot Instance Interruption Warning"]
    detail = {
      "instance-id" = [each.value]
    }
  })

  tags = {
    Name        = "${var.project_name}-ec2-spot-warning-${each.key}"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_cloudwatch_event_target" "ec2_spot_warning_slack" {
  for_each = local.stop_alert_nodes

  rule      = aws_cloudwatch_event_rule.ec2_spot_warning[each.key].name
  target_id = "slack"
  arn       = aws_cloudwatch_event_api_destination.slack[0].arn
  role_arn  = aws_iam_role.ec2_stop_alert[0].arn
  input     = jsonencode({ text = "스팟 회수 예고 · ${each.key} (2분 뒤 중지)" })
}

# 재시도를 다 써도 슬랙에 못 보낸 경우
resource "aws_cloudwatch_metric_alarm" "ec2_spot_warning_alert_failed" {
  for_each = local.stop_alert_nodes

  alarm_name        = "${var.project_name}-ec2-spot-warning-alert-failed-${each.key}"
  alarm_description = "스팟 회수 예고를 슬랙에 보내지 못함 · ${each.key}"

  namespace   = "AWS/Events"
  metric_name = "FailedInvocations"

  dimensions = {
    RuleName = aws_cloudwatch_event_rule.ec2_spot_warning[each.key].name
  }

  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alarms.arn]

  tags = {
    Name        = "${var.project_name}-ec2-spot-warning-alert-failed-${each.key}"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}
