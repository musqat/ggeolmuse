terraform {
  required_version = ">= 1.10"

  # backend 는 변수를 못 써서 리전을 직접 적는다
  backend "s3" {
    bucket       = "ggeolmuse-tfstate-apne2"
    key          = "ggeolmuse/terraform.tfstate"
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

# Data source to get current AWS account ID
data "aws_caller_identity" "current" {}
