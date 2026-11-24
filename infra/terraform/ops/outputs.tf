output "scheduler_role_arn" {
  description = "EventBridge Scheduler 의 EC2 제어 역할 ARN"
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
  description = "RDS 시작 스케줄 (cron / 타임존), rds_instance_id 미설정 시 빈 값"
  value       = local.rds_enabled ? "${aws_scheduler_schedule.rds_start[0].schedule_expression} (${var.scheduler_timezone})" : ""
}

output "rds_stop_schedule" {
  description = "RDS 중지 스케줄 (cron / 타임존), rds_instance_id 미설정 시 빈 값"
  value       = local.rds_enabled ? "${aws_scheduler_schedule.rds_stop[0].schedule_expression} (${var.scheduler_timezone})" : ""
}

output "alarm_topic_arn" {
  description = "알람 발행 SNS 토픽 ARN"
  value       = aws_sns_topic.alarms.arn
}

output "alarm_email" {
  description = "알람 메일 수신 주소"
  value       = var.alarm_email == "" ? "구독 없음 (alarm_email 미설정)" : var.alarm_email
}

output "db_backup_bucket" {
  description = "pg_dump 백업 S3 버킷, helm dbBackup.bucket 과 같은 값"
  value       = aws_s3_bucket.db_backup.bucket
}

output "db_backup_writer_roles" {
  description = "덤프 쓰기 정책 대상 역할"
  value       = var.db_backup_writer_roles
}

output "ci_plan_role_arn" {
  description = "PR 읽기 전용 plan 역할 ARN(저장소 시크릿 TERRAFORM_PLAN_ROLE_ARN), ci_plan_role_enabled 꺼짐 시 빈 값"
  value       = var.ci_plan_role_enabled ? aws_iam_role.ci_plan[0].arn : ""
}
