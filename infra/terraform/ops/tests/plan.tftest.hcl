# 운영 루트 전체를 가짜 AWS 프로바이더로 plan(변수 검사 · 값에 따라 실패하는 식을 validate 뒤에 잡음)
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

# imports.tf 의 자원마다 override_resource(가짜 프로바이더는 import 를 못 함)
override_resource {
  target = aws_db_instance.postgres
}
override_resource {
  target = aws_s3_bucket.tfstate
  values = { arn = "arn:aws:s3:::ggeolmuse-tfstate-apne2" }
}
override_resource {
  target = aws_s3_bucket_versioning.tfstate
}
override_resource {
  target = aws_s3_bucket_server_side_encryption_configuration.tfstate
}
override_resource {
  target = aws_s3_bucket_public_access_block.tfstate
}

variables {
  scheduled_instances = {
    control-plane = "i-0aaaaaaaaaaaaaaa1"
    worker-a      = "i-0bbbbbbbbbbbbbbb2"
    worker-b      = "i-0ccccccccccccccc3"
  }
}

run "plan" {
  command = plan
}

run "ci_plan_role_off" {
  command = plan

  assert {
    condition     = length(aws_iam_role.ci_plan) == 0 && length(aws_iam_openid_connect_provider.github) == 0
    error_message = "ci_plan_role_enabled 가 꺼져 있는데 CI 역할을 만듦"
  }
}

run "ci_plan_role_new_oidc" {
  command = plan

  variables {
    ci_plan_role_enabled = true
  }

  assert {
    condition     = aws_iam_openid_connect_provider.github[0].url == "https://token.actions.githubusercontent.com" && aws_iam_openid_connect_provider.github[0].client_id_list == toset(["sts.amazonaws.com"])
    error_message = "GitHub OIDC 공급자 주소 · 대상이 다름"
  }
}

run "ci_plan_role" {
  command = plan

  variables {
    ci_plan_role_enabled     = true
    github_oidc_provider_arn = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"
  }

  assert {
    condition     = length(aws_iam_openid_connect_provider.github) == 0
    error_message = "계정에 있는 OIDC 공급자를 두고 또 만듦"
  }
  assert {
    condition     = jsondecode(aws_iam_role.ci_plan[0].assume_role_policy).Statement[0].Principal.Federated == "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"
    error_message = "역할의 신뢰 대상이 github_oidc_provider_arn 의 공급자가 아님"
  }
  assert {
    condition = jsondecode(aws_iam_role.ci_plan[0].assume_role_policy).Statement[0].Condition.StringEquals == {
      "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
      "token.actions.githubusercontent.com:sub" = "repo:musqat/ggeolmuse:pull_request"
    }
    error_message = "이 저장소의 PR 실행만 역할을 맡게 하지 않음"
  }
  assert {
    condition = alltrue([
      for s in jsondecode(aws_iam_role_policy.ci_plan[0].policy).Statement : alltrue([
        for a in flatten([s.Action]) :
        can(regex(":(Describe|Get|List)", a))
      ])
    ])
    error_message = "읽기 아닌 동작이 있음"
  }
  assert {
    condition = anytrue([
      for s in jsondecode(aws_iam_role_policy.ci_plan[0].policy).Statement :
      s.Action == "s3:GetObject" && s.Resource == "arn:aws:s3:::ggeolmuse-tfstate-apne2/ggeolmuse/terraform.tfstate"
    ])
    error_message = "상태 파일 읽기가 이 루트의 키 하나로 좁지 않음"
  }
  assert {
    condition = !anytrue([
      for s in jsondecode(aws_iam_role_policy.ci_plan[0].policy).Statement :
      anytrue([for a in flatten([s.Action]) : can(regex("^(secretsmanager|ssm|kms):", a))])
    ])
    error_message = "시크릿 · 파라미터 · 키를 읽는 동작이 있음"
  }
  assert {
    condition = anytrue([
      for s in jsondecode(aws_iam_role_policy.ci_plan[0].policy).Statement :
      contains(flatten([s.Action]), "logs:Describe*") && !anytrue([for a in flatten([s.Action]) : can(regex("^logs:Get", a))])
    ])
    error_message = "로그 그룹 설정은 읽되 로그 내용은 읽지 않음"
  }
}
