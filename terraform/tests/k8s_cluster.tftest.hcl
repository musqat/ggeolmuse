# 루트의 멀티 노드 예비 클러스터 호출(가짜 AWS 프로바이더)
mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = { account_id = "123456789012" }
  }
  mock_data "aws_ami" {
    defaults = { id = "ami-0123456789abcdef0" }
  }
  mock_data "aws_subnet" {
    defaults = { vpc_id = "vpc-0123456789abcdef0" }
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
  mock_resource "aws_lb" {
    defaults = { arn = "arn:aws:elasticloadbalancing:ap-northeast-2:123456789012:loadbalancer/net/mock/0123456789abcdef" }
  }
  mock_resource "aws_lb_target_group" {
    defaults = { arn = "arn:aws:elasticloadbalancing:ap-northeast-2:123456789012:targetgroup/mock/0123456789abcdef" }
  }
}

variables {
  ec2_instance_id = "i-0123456789abcdef0"
}

run "disabled_by_default" {
  command = plan

  assert {
    condition     = length(module.k8s_cluster) == 0
    error_message = "기본값에서는 멀티 노드 리소스가 없어야 함"
  }
}

run "enabled_cluster" {
  command = apply

  variables {
    k8s_cluster_enabled        = true
    k8s_subnet_id              = "subnet-0123456789abcdef0"
    k8s_ingress_prefix_list_id = "pl-0123456789abcdef0"
  }

  assert {
    condition     = toset(keys(module.k8s_cluster[0].node_ids)) == toset(["control-plane", "worker-1", "worker-2"])
    error_message = "노드는 컨트롤 플레인 1 · 워커 2"
  }
}

run "subnet_required_when_enabled" {
  command = plan

  variables {
    k8s_cluster_enabled        = true
    k8s_ingress_prefix_list_id = "pl-0123456789abcdef0"
  }

  expect_failures = [var.k8s_subnet_id]
}
