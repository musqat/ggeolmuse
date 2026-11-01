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

# 인스턴스 안에서 멈춤(스케줄러는 성공이라 위 둘에 안 걸림), 3분 연속 실패만
resource "aws_cloudwatch_metric_alarm" "ec2_status_check" {
  alarm_name        = "${var.project_name}-ec2-status-check"
  alarm_description = "EC2 상태 검사 실패 (인스턴스 또는 호스트 이상)"

  namespace   = "AWS/EC2"
  metric_name = "StatusCheckFailed"

  dimensions = {
    InstanceId = var.ec2_instance_id
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
