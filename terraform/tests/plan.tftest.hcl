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
  target = aws_instance.app
}
override_resource {
  target = aws_security_group.app
}
override_resource {
  target = aws_db_instance.postgres
}
override_resource {
  target = aws_iam_role.node
}
override_resource {
  target = aws_iam_instance_profile.node
}
override_resource {
  target = aws_iam_role_policy_attachments_exclusive.node
}
override_resource {
  target = aws_s3_bucket.tfstate
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
  ec2_instance_id                = "i-0123456789abcdef0"
  ec2_ami_id                     = "ami-0123456789abcdef0"
  ec2_subnet_id                  = "subnet-0123456789abcdef0"
  ec2_key_name                   = null
  ec2_root_volume_size           = 30
  ec2_root_volume_type           = "gp3"
  vpc_id                         = "vpc-0123456789abcdef0"
  ec2_security_group_id          = "sg-0123456789abcdef0"
  ec2_security_group_description = "ggeolmuse k3s node"
  ec2_extra_ingress              = []
  node_role_policy_arns          = []
}

run "plan" {
  command = plan
}
