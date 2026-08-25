output "iam_role_arn" {
  description = "ARN of the IAM role"
  value       = aws_iam_role.ggeolmuse_ec2_secrets_role.arn
}

output "iam_role_name" {
  description = "Name of the IAM role"
  value       = aws_iam_role.ggeolmuse_ec2_secrets_role.name
}

output "instance_profile_arn" {
  description = "ARN of the instance profile"
  value       = aws_iam_instance_profile.ggeolmuse_ec2_profile.arn
}

output "instance_profile_name" {
  description = "Name of the instance profile"
  value       = aws_iam_instance_profile.ggeolmuse_ec2_profile.name
}

output "ec2_instance_id" {
  description = "EC2 instance ID with IAM role attached"
  value       = var.ec2_instance_id
}

output "scheduler_role_arn" {
  description = "ARN of the EventBridge Scheduler role"
  value       = aws_iam_role.ec2_scheduler.arn
}

output "scheduler_start_schedule" {
  description = "EC2 시작 스케줄 (cron / timezone)"
  value       = "${aws_scheduler_schedule.ec2_start.schedule_expression} (${var.scheduler_timezone})"
}

output "scheduler_stop_schedule" {
  description = "EC2 중지 스케줄 (cron / timezone)"
  value       = "${aws_scheduler_schedule.ec2_stop.schedule_expression} (${var.scheduler_timezone})"
}

output "alarm_topic_arn" {
  description = "알람 SNS 토픽 ARN"
  value       = aws_sns_topic.alarms.arn
}

output "alarm_email_pending" {
  description = "이메일 구독 확인 필요 여부 (수신함의 확인 링크를 눌러야 알람이 온다)"
  value       = var.alarm_email == "" ? "구독 없음 (alarm_email 미설정)" : "${var.alarm_email} — 수신함에서 구독 확인 필요"
}
