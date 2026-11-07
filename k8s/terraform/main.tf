# 멀티 노드 예비 클러스터 루트(운영 terraform 과 상태를 따로 둠, apply 하면 만듦)
terraform {
  required_version = ">= 1.10"

  # backend 는 변수를 못 써 리전을 직접 적음
  backend "s3" {
    bucket       = "ggeolmuse-tfstate-apne2"
    key          = "ggeolmuse/k8s-cluster.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}

module "k8s_cluster" {
  source = "./modules/k8s-cluster"

  project_name           = var.project_name
  environment            = var.environment
  subnet_id              = var.subnet_id
  ingress_prefix_list_id = var.ingress_prefix_list_id
  rds_security_group_id  = var.rds_security_group_id
  secret_arns = [
    for name in var.node_secret_names :
    "arn:aws:secretsmanager:${var.aws_region}:${data.aws_caller_identity.current.account_id}:secret:${name}-??????"
  ]
}
