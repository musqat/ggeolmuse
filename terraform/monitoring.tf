# 스케줄러 실패 알림
#
# EventBridge Scheduler 는 실패해도 조용하다. 재시도 3번을 다 쓰면 그대로 끝이고
# 어디에도 안 남는다. 평일 아침에 인스턴스가 안 켜져도 알 방법이 없어서
# CloudWatch 알람으로 잡는다.
#
# 정지 중에는 지표 자체가 안 나온다. 하루 대부분이 정지 상태라
# treat_missing_data 를 notBreaching 으로 둬야 밤마다 울리지 않는다.

resource "aws_sns_topic" "alarms" {
  name = "${var.project_name}-alarms"

  tags = {
    Name        = "${var.project_name}-alarms"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# 이메일 구독은 apply 후 수신함에서 확인 링크를 눌러야 활성화된다.
# 누르기 전까지는 pending confirmation 상태로 남고 알람이 안 온다.
resource "aws_sns_topic_subscription" "alarms_email" {
  count = var.alarm_email == "" ? 0 : 1

  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

# 대상 호출이 오류로 돌아온 경우.
# IAM 권한이 깨졌거나 인스턴스가 terminate 됐을 때 여기 걸린다.
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

# 재시도를 전부 소진하고 호출이 버려진 경우.
# 위 알람보다 심각하다. 그날 스케줄은 아예 실행되지 않았다는 뜻이다.
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

# 인스턴스는 떴는데 안에서 멈춘 경우.
# 스케줄러 쪽은 성공으로 끝나므로 위 두 알람에 안 걸린다.
# 3분 연속 실패해야 울린다. 기동 직후 잠깐 뜨는 실패로는 안 울린다.
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
