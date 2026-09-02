output "iam_role_arn" {
  description = "EC2 가 시크릿을 읽을 때 쓰는 IAM 역할 ARN"
  value       = aws_iam_role.ggeolmuse_ec2_secrets_role.arn
}

output "iam_role_name" {
  description = "EC2 가 시크릿을 읽을 때 쓰는 IAM 역할 이름"
  value       = aws_iam_role.ggeolmuse_ec2_secrets_role.name
}

output "instance_profile_arn" {
  description = "IAM 역할을 EC2 에 붙이는 인스턴스 프로파일 ARN"
  value       = aws_iam_instance_profile.ggeolmuse_ec2_profile.arn
}

output "instance_profile_name" {
  description = "IAM 역할을 EC2 에 붙이는 인스턴스 프로파일 이름"
  value       = aws_iam_instance_profile.ggeolmuse_ec2_profile.name
}

output "ec2_instance_id" {
  description = "IAM 역할을 붙인 EC2 인스턴스 ID"
  value       = var.ec2_instance_id
}

output "scheduler_role_arn" {
  description = "EventBridge Scheduler 가 EC2 를 제어할 때 쓰는 역할 ARN"
  value       = aws_iam_role.ec2_scheduler.arn
}

output "scheduler_start_schedule" {
  description = "EC2 시작 스케줄 (cron / 타임존)"
  value       = "${aws_scheduler_schedule.ec2_start.schedule_expression} (${var.scheduler_timezone})"
}

output "scheduler_stop_schedule" {
  description = "EC2 중지 스케줄 (cron / 타임존)"
  value       = "${aws_scheduler_schedule.ec2_stop.schedule_expression} (${var.scheduler_timezone})"
}

output "rds_start_schedule" {
  description = "RDS 시작 스케줄 (cron / 타임존). rds_instance_id 가 없으면 빈 값"
  value       = local.rds_enabled ? "${aws_scheduler_schedule.rds_start[0].schedule_expression} (${var.scheduler_timezone})" : ""
}

output "rds_stop_schedule" {
  description = "RDS 중지 스케줄 (cron / 타임존). rds_instance_id 가 없으면 빈 값"
  value       = local.rds_enabled ? "${aws_scheduler_schedule.rds_stop[0].schedule_expression} (${var.scheduler_timezone})" : ""
}

output "alarm_topic_arn" {
  description = "알람이 발행되는 SNS 토픽 ARN"
  value       = aws_sns_topic.alarms.arn
}

output "alarm_email_pending" {
  description = "이메일 구독 상태. 수신함의 확인 링크를 눌러야 알람이 온다"
  value       = var.alarm_email == "" ? "구독 없음 (alarm_email 미설정)" : "${var.alarm_email} — 수신함에서 구독 확인 필요"
}
