# 가동 시간 사이트 확인(EventBridge Scheduler 5분마다 → 주소별 규칙이 API Destination 으로 GET, 실패 구간 알람 → 슬랙 · SNS)

locals {
  # 웹훅이 있는지만 쓰므로 민감 값 아님(for_each 키에 씀)
  uptime_enabled       = nonsensitive(local.stop_alert_enabled) && length(var.uptime_check_urls) > 0
  uptime_checks        = local.uptime_enabled ? var.uptime_check_urls : {}
  uptime_source        = "${var.project_name}.uptime"
  uptime_event_bus_arn = "arn:aws:events:${var.aws_region}:${data.aws_caller_identity.current.account_id}:event-bus/default"

  # EC2 시작 20분 뒤(앱 기동 · 아침 확인과 같은 때)부터 중지 시각 전 정시까지
  uptime_first_minutes = var.scheduler_start_hour * 60 + var.scheduler_start_minute + 20
  uptime_first_hour    = floor(local.uptime_first_minutes / 60)
  uptime_schedules = local.uptime_enabled ? {
    first = "cron(${local.uptime_first_minutes % 60}/5 ${local.uptime_first_hour} ? * ${var.scheduler_days} *)"
    rest  = "cron(0/5 ${local.uptime_first_hour + 1}-${var.scheduler_stop_hour - 1} ? * ${var.scheduler_days} *)"
  } : {}

  # 확인 일정 그룹의 실패 지표별 알람(실패가 있을 때만 값)
  uptime_schedule_alarms = local.uptime_enabled ? {
    "target-error"       = { metric = "TargetErrorCount", description = "사이트 확인 일정이 이벤트를 넣지 못함" }
    "invocation-dropped" = { metric = "InvocationDroppedCount", description = "재시도 소진으로 사이트 확인 이벤트가 버려짐" }
  } : {}

  # 알람 상태 변화별 슬랙 문구(<desc> · <name> 은 이벤트 값)
  uptime_notices = local.uptime_enabled ? {
    down = { state = ["ALARM"], previous = ["OK", "INSUFFICIENT_DATA"], text = ":rotating_light: 사이트 확인 실패 · <desc>" }
    up   = { state = ["OK"], previous = ["ALARM"], text = ":white_check_mark: 사이트 확인 복구 · <name>" }
  } : {}
}

# 일정이 이벤트를 넣고 규칙이 확인 주소를 부를 때 쓰는 역할
resource "aws_iam_role" "uptime_check" {
  count = local.uptime_enabled ? 1 : 0

  name        = "${var.project_name}-uptime-check-role"
  description = "Role for EventBridge Scheduler and rules to run uptime checks"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = ["scheduler.amazonaws.com", "events.amazonaws.com"]
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
    Name        = "${var.project_name}-uptime-check-role"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_iam_role_policy" "uptime_check" {
  count = local.uptime_enabled ? 1 : 0

  name = "${var.project_name}-uptime-check-policy"
  role = aws_iam_role.uptime_check[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "events:PutEvents"
        Resource = local.uptime_event_bus_arn
      },
      {
        Effect   = "Allow"
        Action   = "events:InvokeApiDestination"
        Resource = [for d in aws_cloudwatch_event_api_destination.uptime : d.arn]
      }
    ]
  })
}

# 사이트 확인 일정 그룹(일정 실패 알람의 단위)
resource "aws_scheduler_schedule_group" "uptime" {
  count = local.uptime_enabled ? 1 : 0

  name = "${var.project_name}-uptime"

  tags = {
    Name        = "${var.project_name}-uptime"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_scheduler_schedule" "uptime" {
  for_each = local.uptime_schedules

  name       = "${var.project_name}-uptime-${each.key}"
  group_name = aws_scheduler_schedule_group.uptime[0].name
  state      = var.scheduler_enabled ? "ENABLED" : "DISABLED"

  schedule_expression          = each.value
  schedule_expression_timezone = var.scheduler_timezone

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = local.uptime_event_bus_arn
    role_arn = aws_iam_role.uptime_check[0].arn
    input    = jsonencode({})

    eventbridge_parameters {
      detail_type = "uptime-check"
      source      = local.uptime_source
    }

    retry_policy {
      maximum_retry_attempts       = 2
      maximum_event_age_in_seconds = 60
    }
  }

  # 정책 다음에 일정(참조가 없어 terraform 이 순서를 모름)
  depends_on = [aws_iam_role_policy.uptime_check]
}

# 확인 요청도 인증 방식이 필수라 슬랙 연결의 쓰지 않는 헤더를 같이 씀
resource "aws_cloudwatch_event_api_destination" "uptime" {
  for_each = local.uptime_checks

  name                             = "${var.project_name}-uptime-${each.key}"
  description                      = "Uptime check ${each.value}"
  connection_arn                   = aws_cloudwatch_event_connection.slack[0].arn
  invocation_endpoint              = each.value
  http_method                      = "GET"
  invocation_rate_limit_per_second = 1
}

resource "aws_cloudwatch_event_rule" "uptime" {
  for_each = local.uptime_checks

  name        = "${var.project_name}-uptime-check-${each.key}"
  description = "사이트 확인 ${each.value}"

  event_pattern = jsonencode({
    source        = [local.uptime_source]
    "detail-type" = ["uptime-check"]
  })

  tags = {
    Name        = "${var.project_name}-uptime-check-${each.key}"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_cloudwatch_event_target" "uptime" {
  for_each = local.uptime_checks

  rule      = aws_cloudwatch_event_rule.uptime[each.key].name
  target_id = "check"
  arn       = aws_cloudwatch_event_api_destination.uptime[each.key].arn
  role_arn  = aws_iam_role.uptime_check[0].arn
  input     = jsonencode({})

  # 1분 안 재시도까지 실패해야 실패 호출로 셈
  retry_policy {
    maximum_retry_attempts       = 2
    maximum_event_age_in_seconds = 60
  }
}

# 확인이 있었던 5분 구간만 값(구간 안 실패 호출 수, 없으면 0), 확인이 없는 구간은 상태 그대로(ignore)
resource "aws_cloudwatch_metric_alarm" "uptime" {
  for_each = local.uptime_checks

  alarm_name        = "${var.project_name}-uptime-${each.key}"
  alarm_description = "${each.value} 응답 실패(2xx 아님 · 시간 초과)"

  metric_query {
    id          = "e1"
    expression  = "IF(m1 > 0, FILL(m2, 0))"
    label       = "확인 실패 수"
    return_data = true
  }

  metric_query {
    id = "m1"

    metric {
      namespace   = "AWS/Events"
      metric_name = "Invocations"
      dimensions  = { RuleName = aws_cloudwatch_event_rule.uptime[each.key].name }
      stat        = "Sum"
      period      = 300
    }
  }

  metric_query {
    id = "m2"

    metric {
      namespace   = "AWS/Events"
      metric_name = "FailedInvocations"
      dimensions  = { RuleName = aws_cloudwatch_event_rule.uptime[each.key].name }
      stat        = "Sum"
      period      = 300
    }
  }

  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "ignore"

  alarm_actions = [aws_sns_topic.alarms.arn]

  tags = {
    Name        = "${var.project_name}-uptime-${each.key}"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# 확인 일정의 대상 호출 오류 · 재시도 소진(IAM 권한 깨짐 등)
resource "aws_cloudwatch_metric_alarm" "uptime_schedule" {
  for_each = local.uptime_schedule_alarms

  alarm_name        = "${var.project_name}-uptime-schedule-${each.key}"
  alarm_description = each.value.description

  namespace   = "AWS/Scheduler"
  metric_name = each.value.metric

  dimensions = {
    ScheduleGroup = aws_scheduler_schedule_group.uptime[0].name
  }

  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alarms.arn]

  tags = {
    Name        = "${var.project_name}-uptime-schedule-${each.key}"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# 사이트 확인 알람의 상태 변화 → 기존 슬랙 API Destination
resource "aws_cloudwatch_event_rule" "uptime_notice" {
  for_each = local.uptime_notices

  name        = "${var.project_name}-uptime-notice-${each.key}"
  description = "사이트 확인 알람 ${join(" · ", each.value.state)}"

  event_pattern = jsonencode({
    source        = ["aws.cloudwatch"]
    "detail-type" = ["CloudWatch Alarm State Change"]
    detail = {
      alarmName     = [for a in aws_cloudwatch_metric_alarm.uptime : a.alarm_name]
      state         = { value = each.value.state }
      previousState = { value = each.value.previous }
    }
  })

  tags = {
    Name        = "${var.project_name}-uptime-notice-${each.key}"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_cloudwatch_event_target" "uptime_notice" {
  for_each = local.uptime_notices

  rule      = aws_cloudwatch_event_rule.uptime_notice[each.key].name
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
