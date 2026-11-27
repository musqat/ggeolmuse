# 노드 · RDS 중지 이벤트와 스팟 회수 예고 원문 기록, 슬랙 알림과 같이 웹훅이 있을 때만
resource "aws_cloudwatch_log_group" "stop_alerts" {
  count = local.stop_alert_enabled ? 1 : 0

  name              = "/aws/events/${var.project_name}-stop-alerts"
  retention_in_days = 30

  tags = {
    Name        = "${var.project_name}-stop-alerts"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# EventBridge 가 위 로그 그룹에 쓰는 권한
resource "aws_cloudwatch_log_resource_policy" "stop_alerts" {
  count = local.stop_alert_enabled ? 1 : 0

  policy_name = "${var.project_name}-stop-alerts"
  policy_document = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = { Service = "events.amazonaws.com" }
        Action    = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource  = "${aws_cloudwatch_log_group.stop_alerts[0].arn}:*"
      }
    ]
  })
}

resource "aws_cloudwatch_event_target" "ec2_stopped_log" {
  for_each = local.stop_alert_nodes

  rule      = aws_cloudwatch_event_rule.ec2_stopped[each.key].name
  target_id = "log"
  arn       = aws_cloudwatch_log_group.stop_alerts[0].arn
}

resource "aws_cloudwatch_event_target" "rds_stopped_log" {
  count = local.rds_stop_alert

  rule      = aws_cloudwatch_event_rule.rds_stopped[0].name
  target_id = "log"
  arn       = aws_cloudwatch_log_group.stop_alerts[0].arn
}

resource "aws_cloudwatch_event_target" "ec2_spot_warning_log" {
  for_each = local.stop_alert_nodes

  rule      = aws_cloudwatch_event_rule.ec2_spot_warning[each.key].name
  target_id = "log"
  arn       = aws_cloudwatch_log_group.stop_alerts[0].arn
}
