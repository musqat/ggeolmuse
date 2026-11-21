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
}

variables {
  subnet_id = "subnet-0123456789abcdef0"
}

run "cluster" {
  command = apply

  assert {
    condition     = toset(keys(module.k8s_cluster.node_ids)) == toset(["control-plane", "worker-a", "worker-b"])
    error_message = "노드는 컨트롤 플레인 1 · large 워커 둘"
  }
}

run "subnet_required" {
  command = plan

  variables {
    subnet_id = ""
  }

  expect_failures = [var.subnet_id]
}
