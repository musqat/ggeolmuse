# EventBridge Scheduler 실패 알림(재시도를 다 써도 따로 남지 않아 CloudWatch 알람으로)
# 꺼져 있는 동안은 지표가 없어 treat_missing_data 는 notBreaching

resource "aws_sns_topic" "alarms" {
  name = "${var.project_name}-alarms"

  tags = {
    Name        = "${var.project_name}-alarms"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# 이메일 구독(apply 뒤 수신함의 확인 링크를 누르기 전엔 pending confirmation, 알람 안 옴)
resource "aws_sns_topic_subscription" "alarms_email" {
  count = var.alarm_email == "" ? 0 : 1

  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

# 스케줄 대상 호출 오류(IAM 권한 깨짐 · 인스턴스 terminate)
resource "aws_cloudwatch_metric_alarm" "scheduler_target_error" {
  alarm_name        = "${var.project_name}-scheduler-target-error"
  alarm_description = "EC2 시작/중지 스케줄이 EC2 API 호출에 실패"

  namespace   = "AWS/Scheduler"
  metric_name = "TargetErrorCount"

  dimensions = {
    ScheduleGroup = aws_scheduler_schedule_group.ec2_scheduler.name
  }

  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alarms.arn]

  tags = {
    Name        = "${var.project_name}-scheduler-target-error"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# 재시도를 다 쓰고 버려진 호출(그날 스케줄이 아예 안 돎, 위보다 심각)
resource "aws_cloudwatch_metric_alarm" "scheduler_invocation_dropped" {
  alarm_name        = "${var.project_name}-scheduler-invocation-dropped"
  alarm_description = "재시도 소진으로 EC2 시작/중지 호출이 버려짐"

  namespace   = "AWS/Scheduler"
  metric_name = "InvocationDroppedCount"

  dimensions = {
    ScheduleGroup = aws_scheduler_schedule_group.ec2_scheduler.name
  }

  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alarms.arn]

  tags = {
    Name        = "${var.project_name}-scheduler-invocation-dropped"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# RDS 시작 · 중지 스케줄의 대상 호출 오류
resource "aws_cloudwatch_metric_alarm" "rds_scheduler_target_error" {
  count = local.rds_resource

  alarm_name        = "${var.project_name}-rds-scheduler-target-error"
  alarm_description = "RDS 시작/중지 스케줄이 RDS API 호출에 실패"

  namespace   = "AWS/Scheduler"
  metric_name = "TargetErrorCount"

  dimensions = {
    ScheduleGroup = aws_scheduler_schedule_group.rds_scheduler[0].name
  }

  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alarms.arn]

  tags = {
    Name        = "${var.project_name}-rds-scheduler-target-error"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# 재시도를 다 쓰고 버려진 RDS 시작 · 중지 호출
resource "aws_cloudwatch_metric_alarm" "rds_scheduler_invocation_dropped" {
  count = local.rds_resource

  alarm_name        = "${var.project_name}-rds-scheduler-invocation-dropped"
  alarm_description = "재시도 소진으로 RDS 시작/중지 호출이 버려짐"

  namespace   = "AWS/Scheduler"
  metric_name = "InvocationDroppedCount"

  dimensions = {
    ScheduleGroup = aws_scheduler_schedule_group.rds_scheduler[0].name
  }

  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alarms.arn]

  tags = {
    Name        = "${var.project_name}-rds-scheduler-invocation-dropped"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# 인스턴스 안에서 멈춤(스케줄러는 성공이라 위 둘에 안 걸림), 3분 연속 실패만
resource "aws_cloudwatch_metric_alarm" "ec2_status_check" {
  for_each = local.scheduled_instances

  alarm_name        = "${var.project_name}-ec2-status-check-${each.key}"
  alarm_description = "EC2 상태 검사 실패 (인스턴스 또는 호스트 이상)"

  namespace   = "AWS/EC2"
  metric_name = "StatusCheckFailed"

  dimensions = {
    InstanceId = each.value
  }

  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 3
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alarms.arn]

  tags = {
    Name        = "${var.project_name}-ec2-status-check"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# EC2 · RDS · 사이트 확인 스케줄 실패 알람 → 기존 슬랙 API Destination(웹훅이 없으면 이메일만)
resource "aws_cloudwatch_event_rule" "schedule_alarm_notice" {
  count = nonsensitive(local.stop_alert_enabled) ? 1 : 0

  name        = "${var.project_name}-schedule-alarm-notice"
  description = "스케줄 실패 알람 ALARM"

  event_pattern = jsonencode({
    source        = ["aws.cloudwatch"]
    "detail-type" = ["CloudWatch Alarm State Change"]
    detail = {
      alarmName = concat(
        [aws_cloudwatch_metric_alarm.scheduler_target_error.alarm_name, aws_cloudwatch_metric_alarm.scheduler_invocation_dropped.alarm_name],
        aws_cloudwatch_metric_alarm.rds_scheduler_target_error[*].alarm_name,
        aws_cloudwatch_metric_alarm.rds_scheduler_invocation_dropped[*].alarm_name,
        [for a in aws_cloudwatch_metric_alarm.uptime_schedule : a.alarm_name],
      )
      state = { value = ["ALARM"] }
    }
  })

  tags = {
    Name        = "${var.project_name}-schedule-alarm-notice"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_cloudwatch_event_target" "schedule_alarm_notice" {
  count = length(aws_cloudwatch_event_rule.schedule_alarm_notice)

  rule      = aws_cloudwatch_event_rule.schedule_alarm_notice[0].name
  target_id = "slack"
  arn       = aws_cloudwatch_event_api_destination.slack[0].arn
  role_arn  = aws_iam_role.ec2_stop_alert[0].arn

  input_transformer {
    input_paths = {
      desc = "$.detail.configuration.description"
    }
    input_template = "{\"text\": \"스케줄 실패 · <desc>\"}"
  }
}

# 재시도를 다 써도 슬랙에 못 보낸 경우
resource "aws_cloudwatch_metric_alarm" "schedule_alarm_notice_failed" {
  count = length(aws_cloudwatch_event_rule.schedule_alarm_notice)

  alarm_name        = "${var.project_name}-schedule-alarm-notice-failed"
  alarm_description = "스케줄 실패 알람을 슬랙에 보내지 못함"

  namespace   = "AWS/Events"
  metric_name = "FailedInvocations"

  dimensions = {
    RuleName = aws_cloudwatch_event_rule.schedule_alarm_notice[0].name
  }

  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alarms.arn]

  tags = {
    Name        = "${var.project_name}-schedule-alarm-notice-failed"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}
