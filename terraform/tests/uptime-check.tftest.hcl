# 가동 시간 사이트 확인(일정 · 주소별 확인 · 알람 · 슬랙 알림)을 가짜 AWS 프로바이더로 확인
mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = { account_id = "123456789012" }
  }
  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::123456789012:role/mock" }
  }
  mock_resource "aws_sns_topic" {
    defaults = { arn = "arn:aws:sns:ap-northeast-2:123456789012:mock" }
  }
  mock_resource "aws_cloudwatch_event_connection" {
    defaults = { arn = "arn:aws:events:ap-northeast-2:123456789012:connection/mock/mock" }
  }
  mock_resource "aws_cloudwatch_event_api_destination" {
    defaults = { arn = "arn:aws:events:ap-northeast-2:123456789012:api-destination/mock/mock" }
  }
}

# 슬랙 API Destination 만 다른 ARN(주소별 확인 대상과 구별)
override_resource {
  target = aws_cloudwatch_event_api_destination.slack
  values = { arn = "arn:aws:events:ap-northeast-2:123456789012:api-destination/ggeolmuse-slack/x" }
}

variables {
  ec2_instance_id   = "i-0123456789abcdef0"
  slack_webhook_url = "https://hooks.slack.com/services/T000/B000/XXXX"
}

run "operating_hours_every_five_minutes" {
  command = plan

  assert {
    condition     = aws_scheduler_schedule.uptime["first"].schedule_expression == "cron(20/5 7 ? * MON-FRI *)"
    error_message = "첫 확인은 EC2 시작 20분 뒤부터 5분마다"
  }

  assert {
    condition     = aws_scheduler_schedule.uptime["rest"].schedule_expression == "cron(0/5 8-18 ? * MON-FRI *)"
    error_message = "나머지 확인은 중지 시각 전 정시까지 5분마다"
  }

  assert {
    condition     = alltrue([for s in aws_scheduler_schedule.uptime : s.schedule_expression_timezone == "Asia/Seoul" && s.state == "ENABLED"])
    error_message = "확인 일정은 EC2 스케줄과 같은 타임존 · 켜짐"
  }

  assert {
    condition     = length(aws_scheduler_schedule.uptime) == 2
    error_message = "확인 일정 두 개"
  }
}

run "each_url_checked_with_get" {
  command = plan

  assert {
    condition     = toset(keys(aws_cloudwatch_event_api_destination.uptime)) == toset(["frontend", "price", "auth"])
    error_message = "프론트 · 시세 API · /auth 세 주소"
  }

  assert {
    condition     = aws_cloudwatch_event_api_destination.uptime["price"].invocation_endpoint == "https://ggeolmuse.com/api/market/price/AAPL"
    error_message = "시세 API 주소"
  }

  assert {
    condition     = aws_cloudwatch_event_api_destination.uptime["auth"].invocation_endpoint == "https://ggeolmuse.com/auth/realms/muscathan"
    error_message = "/auth 는 realm 주소"
  }

  assert {
    condition     = alltrue([for d in aws_cloudwatch_event_api_destination.uptime : d.http_method == "GET"])
    error_message = "확인 요청은 GET"
  }

  assert {
    condition = alltrue([
      for k, r in aws_cloudwatch_event_rule.uptime :
      jsondecode(r.event_pattern).source == ["ggeolmuse.uptime"]
    ])
    error_message = "주소별 규칙은 확인 일정이 넣은 이벤트만 받음"
  }
}

run "failed_check_alarms_only_checked_periods" {
  command = plan

  assert {
    condition     = length(aws_cloudwatch_metric_alarm.uptime) == 3
    error_message = "주소마다 알람 하나"
  }

  assert {
    condition = alltrue([
      for k, a in aws_cloudwatch_metric_alarm.uptime :
      toset([
        for q in a.metric_query :
        "${q.id} ${q.metric[0].namespace} ${q.metric[0].metric_name} ${q.metric[0].dimensions.RuleName} ${q.metric[0].stat} ${q.metric[0].period}"
        if length(q.metric) > 0
        ]) == toset([
        "m1 AWS/Events Invocations ggeolmuse-uptime-check-${k} Sum 300",
        "m2 AWS/Events FailedInvocations ggeolmuse-uptime-check-${k} Sum 300",
      ])
    ])
    error_message = "주소별 규칙의 호출 수 · 실패 호출 수를 5분 합으로 읽음"
  }

  assert {
    condition = alltrue([
      for a in aws_cloudwatch_metric_alarm.uptime :
      [for q in a.metric_query : q.expression if q.return_data == true] == ["IF(m1 > 0, FILL(m2, 0))"]
    ])
    error_message = "확인이 있었던 구간에만 실패 수(없으면 0)가 값으로 나옴"
  }

  assert {
    condition = alltrue([
      for a in aws_cloudwatch_metric_alarm.uptime :
      a.evaluation_periods == 1 && a.threshold == 0 && a.comparison_operator == "GreaterThanThreshold" && a.treat_missing_data == "ignore"
    ])
    error_message = "5분 구간에 실패가 하나라도 있으면 알람, 확인이 없는 시간은 상태 그대로"
  }
}

run "schedule_failures_alarm" {
  command = apply

  assert {
    condition     = aws_scheduler_schedule_group.uptime[0].name == "ggeolmuse-uptime"
    error_message = "확인 일정 전용 그룹"
  }

  assert {
    condition     = alltrue([for s in aws_scheduler_schedule.uptime : s.group_name == "ggeolmuse-uptime"])
    error_message = "확인 일정은 전용 그룹 안"
  }

  assert {
    condition = toset([
      for a in aws_cloudwatch_metric_alarm.uptime_schedule :
      "${a.namespace} ${a.metric_name} ${a.dimensions.ScheduleGroup}"
      ]) == toset([
      "AWS/Scheduler TargetErrorCount ggeolmuse-uptime",
      "AWS/Scheduler InvocationDroppedCount ggeolmuse-uptime",
    ])
    error_message = "전용 그룹의 대상 호출 오류 · 버려진 호출을 보는 알람"
  }

  assert {
    condition = alltrue([
      for a in aws_cloudwatch_metric_alarm.uptime_schedule :
      a.threshold == 0 && a.comparison_operator == "GreaterThanThreshold" && a.treat_missing_data == "notBreaching" && a.alarm_actions == toset([aws_sns_topic.alarms.arn])
    ])
    error_message = "일정 실패가 하나라도 있으면 SNS(이메일)로"
  }
}

run "alarm_changes_go_to_slack" {
  command = apply

  assert {
    condition = alltrue([
      for t in aws_cloudwatch_event_target.uptime_notice :
      t.arn == "arn:aws:events:ap-northeast-2:123456789012:api-destination/ggeolmuse-slack/x"
    ])
    error_message = "알람 상태 변화는 기존 슬랙 API Destination 으로"
  }

  assert {
    condition = alltrue([
      for k, t in aws_cloudwatch_event_target.uptime :
      t.arn == aws_cloudwatch_event_api_destination.uptime[k].arn && t.arn != aws_cloudwatch_event_api_destination.slack[0].arn
    ])
    error_message = "확인 규칙은 주소별 API Destination 으로, 슬랙으로는 안 감"
  }

  assert {
    condition = (
      toset(jsondecode(aws_iam_role_policy.uptime_check[0].policy).Statement[1].Resource) == toset([for d in aws_cloudwatch_event_api_destination.uptime : d.arn])
      && !contains(jsondecode(aws_iam_role_policy.uptime_check[0].policy).Statement[1].Resource, aws_cloudwatch_event_api_destination.slack[0].arn)
    )
    error_message = "확인 역할은 주소별 API Destination 만 부름"
  }

  assert {
    condition = alltrue([
      for a in aws_cloudwatch_metric_alarm.uptime :
      a.alarm_actions == toset([aws_sns_topic.alarms.arn])
    ])
    error_message = "알람은 SNS(이메일)로도"
  }

  assert {
    condition = toset(jsondecode(aws_cloudwatch_event_rule.uptime_notice["down"].event_pattern).detail.alarmName) == toset([
      for a in aws_cloudwatch_metric_alarm.uptime : a.alarm_name
    ])
    error_message = "슬랙 알림 규칙은 사이트 확인 알람만 받음"
  }

  assert {
    condition     = jsondecode(aws_cloudwatch_event_rule.uptime_notice["up"].event_pattern).detail.previousState.value == ["ALARM"]
    error_message = "복구 알림은 ALARM 에서 OK 로 바뀔 때만"
  }

  assert {
    condition     = jsondecode(aws_cloudwatch_event_target.uptime_notice["down"].input_transformer[0].input_template).text == ":rotating_light: 사이트 확인 실패 · <desc>"
    error_message = "실패 알림 문구는 슬랙 웹훅이 받는 JSON 이고 알람 설명이 들어감"
  }
}

run "follows_ec2_start_time" {
  command = plan

  variables {
    scheduler_start_hour   = 6
    scheduler_start_minute = 50
  }

  assert {
    condition     = aws_scheduler_schedule.uptime["first"].schedule_expression == "cron(10/5 7 ? * MON-FRI *)"
    error_message = "06:50 시작이면 07:10 부터"
  }

  assert {
    condition     = aws_scheduler_schedule.uptime["rest"].schedule_expression == "cron(0/5 8-18 ? * MON-FRI *)"
    error_message = "다음 정시부터 중지 전 정시까지"
  }
}

run "disabled_with_ec2_schedule" {
  command = plan

  variables {
    scheduler_enabled = false
  }

  assert {
    condition     = length(aws_scheduler_schedule.uptime) == 2 && alltrue([for s in aws_scheduler_schedule.uptime : s.state == "DISABLED"])
    error_message = "EC2 스케줄을 끄면 확인 일정도 꺼짐"
  }
}

run "nothing_without_slack" {
  command = plan

  variables {
    slack_webhook_url = ""
  }

  assert {
    condition = (
      length(aws_scheduler_schedule.uptime) == 0 && length(aws_scheduler_schedule_group.uptime) == 0
      && length(aws_cloudwatch_event_api_destination.uptime) == 0
      && length(aws_cloudwatch_metric_alarm.uptime) == 0 && length(aws_cloudwatch_metric_alarm.uptime_schedule) == 0
    )
    error_message = "슬랙 웹훅이 없으면 사이트 확인 없음"
  }
}

run "rejects_plain_http" {
  command = plan

  variables {
    uptime_check_urls = { frontend = "http://ggeolmuse.com/" }
  }

  expect_failures = [var.uptime_check_urls]
}
