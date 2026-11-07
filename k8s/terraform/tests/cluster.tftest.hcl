# 멀티 노드 루트의 모듈 호출(가짜 AWS 프로바이더)
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
  mock_resource "aws_lb" {
    defaults = { arn = "arn:aws:elasticloadbalancing:ap-northeast-2:123456789012:loadbalancer/net/mock/0123456789abcdef" }
  }
  mock_resource "aws_lb_target_group" {
    defaults = { arn = "arn:aws:elasticloadbalancing:ap-northeast-2:123456789012:targetgroup/mock/0123456789abcdef" }
  }
}

variables {
  subnet_id              = "subnet-0123456789abcdef0"
  ingress_prefix_list_id = "pl-0123456789abcdef0"
}

run "cluster" {
  command = apply

  assert {
    condition     = toset(keys(module.k8s_cluster.node_ids)) == toset(["control-plane", "worker-1", "worker-2"])
    error_message = "노드는 컨트롤 플레인 1 · 워커 2"
  }
}

run "subnet_required" {
  command = plan

  variables {
    subnet_id = ""
  }

  expect_failures = [var.subnet_id]
}
