# RDS 알람(CPU · 남은 저장 공간 · 연결 수) → SNS 이메일 · 슬랙, RDS 시작 · 중지 → 슬랙
# 꺼져 있는 밤 · 주말은 지표가 없어 treat_missing_data 는 notBreaching

locals {
  rds_alarms = local.rds_enabled ? {
    cpu = {
      metric      = "CPUUtilization"
      statistic   = "Average"
      comparison  = "GreaterThanThreshold"
      threshold   = var.rds_alarm_cpu_percent
      periods     = 3
      description = "RDS CPU ${var.rds_alarm_cpu_percent}% 초과 · 15분 지속"
    }
    storage = {
      metric      = "FreeStorageSpace"
      statistic   = "Minimum"
      comparison  = "LessThanThreshold"
      threshold   = var.rds_alarm_free_storage_gib * 1024 * 1024 * 1024
      periods     = 1
      description = "RDS 남은 저장 공간 ${var.rds_alarm_free_storage_gib}GiB 미만"
    }
    connections = {
      metric      = "DatabaseConnections"
      statistic   = "Maximum"
      comparison  = "GreaterThanThreshold"
      threshold   = var.rds_alarm_connections
      periods     = 2
      description = "RDS 연결 ${var.rds_alarm_connections}개 초과 · 10분 지속"
    }
  } : {}

  # 알람 상태 변화별 슬랙 문구(<desc> · <name> 은 이벤트 값, 웹훅이 있는지만 쓰므로 민감 값 아님)
  rds_alarm_notices = local.rds_enabled && nonsensitive(local.stop_alert_enabled) ? {
    alarm = { state = ["ALARM"], previous = ["OK", "INSUFFICIENT_DATA"], text = "RDS 알람 · <desc>" }
    ok    = { state = ["OK"], previous = ["ALARM"], text = "RDS 알람 복구 · <name>" }
  } : {}

  rds_stop_alert = local.rds_enabled && nonsensitive(local.stop_alert_enabled) ? 1 : 0
}

resource "aws_cloudwatch_metric_alarm" "rds" {
  for_each = local.rds_alarms

  alarm_name        = "${var.project_name}-rds-${each.key}"
  alarm_description = each.value.description

  namespace   = "AWS/RDS"
  metric_name = each.value.metric

  dimensions = {
    DBInstanceIdentifier = var.rds_instance_id
  }

  statistic           = each.value.statistic
  period              = 300
  evaluation_periods  = each.value.periods
  threshold           = each.value.threshold
  comparison_operator = each.value.comparison
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alarms.arn]

  tags = {
    Name        = "${var.project_name}-rds-${each.key}"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# RDS 알람의 상태 변화 → 기존 슬랙 API Destination
resource "aws_cloudwatch_event_rule" "rds_alarm_notice" {
  for_each = local.rds_alarm_notices

  name        = "${var.project_name}-rds-alarm-notice-${each.key}"
  description = "RDS 알람 ${join(" · ", each.value.state)}"

  event_pattern = jsonencode({
    source        = ["aws.cloudwatch"]
    "detail-type" = ["CloudWatch Alarm State Change"]
    detail = {
      alarmName     = [for a in aws_cloudwatch_metric_alarm.rds : a.alarm_name]
      state         = { value = each.value.state }
      previousState = { value = each.value.previous }
    }
  })

  tags = {
    Name        = "${var.project_name}-rds-alarm-notice-${each.key}"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_cloudwatch_event_target" "rds_alarm_notice" {
  for_each = local.rds_alarm_notices

  rule      = aws_cloudwatch_event_rule.rds_alarm_notice[each.key].name
  target_id = "slack"
  arn       = aws_cloudwatch_event_api_destination.slack[0].arn
  role_arn  = aws_iam_role.ec2_stop_alert[0].arn

  input_transformer {
    input_paths = {
      name = "$.detail.alarmName"
      desc = "$.detail.configuration.description"
    }
    input_template = "{\"text\": \"${each.value.text}\"}"
  }
}

# RDS 중지 이벤트 → 기존 슬랙 API Destination
resource "aws_cloudwatch_event_rule" "rds_stopped" {
  count = local.rds_stop_alert

  name        = "${var.project_name}-rds-stopped"
  description = "RDS 중지 이벤트 · ${var.rds_instance_id}"

  event_pattern = jsonencode({
    source        = ["aws.rds"]
    "detail-type" = ["RDS DB Instance Event"]
    detail = {
      EventID          = ["RDS-EVENT-0087"]
      SourceIdentifier = [var.rds_instance_id]
    }
  })

  tags = {
    Name        = "${var.project_name}-rds-stopped"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_cloudwatch_event_target" "rds_stopped_slack" {
  count = local.rds_stop_alert

  rule      = aws_cloudwatch_event_rule.rds_stopped[0].name
  target_id = "slack"
  arn       = aws_cloudwatch_event_api_destination.slack[0].arn
  role_arn  = aws_iam_role.ec2_stop_alert[0].arn
  input     = jsonencode({ text = "RDS 중지 · ${var.rds_instance_id}" })
}

# 재시도를 다 써도 슬랙에 못 보낸 경우
resource "aws_cloudwatch_metric_alarm" "rds_stop_alert_failed" {
  count = local.rds_stop_alert

  alarm_name        = "${var.project_name}-rds-stop-alert-failed"
  alarm_description = "RDS 중지 알림을 슬랙에 보내지 못함"

  namespace   = "AWS/Events"
  metric_name = "FailedInvocations"

  dimensions = {
    RuleName = aws_cloudwatch_event_rule.rds_stopped[0].name
  }

  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alarms.arn]

  tags = {
    Name        = "${var.project_name}-rds-stop-alert-failed"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# RDS 시작 이벤트 → 기존 슬랙 API Destination
resource "aws_cloudwatch_event_rule" "rds_started" {
  count = local.rds_stop_alert

  name        = "${var.project_name}-rds-started"
  description = "RDS 시작 이벤트 · ${var.rds_instance_id}"

  event_pattern = jsonencode({
    source        = ["aws.rds"]
    "detail-type" = ["RDS DB Instance Event"]
    detail = {
      EventID          = ["RDS-EVENT-0088"]
      SourceIdentifier = [var.rds_instance_id]
    }
  })

  tags = {
    Name        = "${var.project_name}-rds-started"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_cloudwatch_event_target" "rds_started_slack" {
  count = local.rds_stop_alert

  rule      = aws_cloudwatch_event_rule.rds_started[0].name
  target_id = "slack"
  arn       = aws_cloudwatch_event_api_destination.slack[0].arn
  role_arn  = aws_iam_role.ec2_stop_alert[0].arn
  input     = jsonencode({ text = "RDS 시작 · ${var.rds_instance_id}" })
}

# 재시도를 다 써도 슬랙에 못 보낸 경우
resource "aws_cloudwatch_metric_alarm" "rds_start_alert_failed" {
  count = local.rds_stop_alert

  alarm_name        = "${var.project_name}-rds-start-alert-failed"
  alarm_description = "RDS 시작 알림을 슬랙에 보내지 못함"

  namespace   = "AWS/Events"
  metric_name = "FailedInvocations"

  dimensions = {
    RuleName = aws_cloudwatch_event_rule.rds_started[0].name
  }

  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alarms.arn]

  tags = {
    Name        = "${var.project_name}-rds-start-alert-failed"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}
