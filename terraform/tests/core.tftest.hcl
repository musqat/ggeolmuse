# 운영 핵심 자원(EC2 · 보안 그룹 · RDS · 노드 역할 · 상태 버킷) import 뒤 plan
# 가짜 프로바이더는 import 를 못 해 가져오는 자원마다 override_resource
mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = { account_id = "123456789012" }
  }
  mock_data "aws_ec2_managed_prefix_list" {
    defaults = { id = "pl-0cloudflare0000000" }
  }
  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::123456789012:role/mock" }
  }
  mock_resource "aws_sns_topic" {
    defaults = { arn = "arn:aws:sns:ap-northeast-2:123456789012:mock" }
  }
}

override_resource {
  target = aws_instance.app
  values = { id = "i-0123456789abcdef0" }
}
override_resource {
  target = aws_security_group.app
  values = { id = "sg-0123456789abcdef0" }
}
override_resource {
  target = aws_db_instance.postgres
}
override_resource {
  target = aws_iam_role.node
  values = { arn = "arn:aws:iam::123456789012:role/ggeolmuse-ec2-role" }
}
override_resource {
  target = aws_iam_instance_profile.node
}
override_resource {
  target = aws_iam_role_policy_attachments_exclusive.node
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
  ec2_instance_id                = "i-0123456789abcdef0"
  ec2_ami_id                     = "ami-0123456789abcdef0"
  ec2_subnet_id                  = "subnet-0123456789abcdef0"
  ec2_key_name                   = "ggeolmuse-key"
  ec2_root_volume_size           = 30
  ec2_root_volume_type           = "gp3"
  vpc_id                         = "vpc-0123456789abcdef0"
  ec2_security_group_id          = "sg-0123456789abcdef0"
  ec2_security_group_description = "ggeolmuse k3s node"
  ec2_extra_ingress = [
    { description = "ssh", protocol = "tcp", from_port = 22, to_port = 22, cidr_blocks = ["203.0.113.10/32"] },
  ]
  node_role_policy_arns = ["arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"]
}

run "ec2" {
  command = plan

  assert {
    condition     = aws_instance.app.instance_type == "t3a.xlarge"
    error_message = "인스턴스 종류가 운영(t3a.xlarge)과 다름"
  }
  assert {
    condition     = aws_instance.app.metadata_options[0].http_tokens == "required" && aws_instance.app.metadata_options[0].http_put_response_hop_limit == 2
    error_message = "IMDSv2 만 · hop 2 가 아님(파드의 External Secrets · db-backup 이 노드 역할을 못 씀)"
  }
  assert {
    condition     = aws_instance.app.iam_instance_profile == "ggeolmuse-ec2-role"
    error_message = "인스턴스 프로파일이 실제로 연결된 ggeolmuse-ec2-role 이 아님"
  }
  assert {
    condition     = contains(aws_instance.app.vpc_security_group_ids, "sg-0123456789abcdef0")
    error_message = "EC2 에 가져온 보안 그룹이 없음"
  }
  assert {
    condition     = aws_instance.app.root_block_device[0].volume_size == 30 && aws_instance.app.root_block_device[0].volume_type == "gp3"
    error_message = "루트 볼륨이 변수 값과 다름"
  }
  assert {
    condition     = aws_instance.app.tags["Name"] == "ggeolmuse"
    error_message = "Name 태그가 README 의 찾기 조건(ggeolmuse)과 다름"
  }
}

run "ec2_extra_security_groups" {
  command = plan

  variables {
    ec2_extra_security_group_ids = ["sg-0aaaaaaaaaaaaaaaa"]
  }

  assert {
    condition     = aws_instance.app.vpc_security_group_ids == toset(["sg-0123456789abcdef0", "sg-0aaaaaaaaaaaaaaaa"])
    error_message = "EC2 에 달린 추가 보안 그룹이 빠짐"
  }
}

run "security_group" {
  command = plan

  assert {
    condition     = aws_security_group.app.name == "ggeolmuse-sg"
    error_message = "보안 그룹 이름이 운영(ggeolmuse-sg)과 다름"
  }
  assert {
    condition = anytrue([
      for r in aws_security_group.app.ingress :
      r.from_port == 443 && r.to_port == 443 && r.protocol == "tcp" && tolist(r.prefix_list_ids) == tolist(["pl-0cloudflare0000000"]) && try(length(r.cidr_blocks), 0) == 0
    ])
    error_message = "443 을 Cloudflare 접두사 목록에서만 받지 않음"
  }
  assert {
    condition     = !anytrue([for r in aws_security_group.app.ingress : r.from_port <= 80 && r.to_port >= 80])
    error_message = "80 규칙이 남음(Full (strict) 뒤 지움)"
  }
  assert {
    condition = anytrue([
      for r in aws_security_group.app.ingress :
      r.from_port == 22 && tolist(r.cidr_blocks) == tolist(["203.0.113.10/32"]) && r.description == "ssh"
    ])
    error_message = "ec2_extra_ingress 규칙이 그룹에 없음"
  }
  assert {
    condition     = length(aws_security_group.app.ingress) == 2
    error_message = "443 · 변수 규칙 말고도 인바운드가 있음"
  }
}

run "node_role" {
  command = plan

  assert {
    condition     = jsondecode(aws_iam_role.node.assume_role_policy).Statement[0].Principal.Service == "ec2.amazonaws.com"
    error_message = "노드 역할을 EC2 가 맡지 못함"
  }
  assert {
    condition     = aws_iam_role.node.description == "Allows EC2 instances to ggeolmuse"
    error_message = "노드 역할 설명이 운영 값과 다름"
  }
  assert {
    condition     = aws_iam_instance_profile.node.role == "ggeolmuse-ec2-role"
    error_message = "프로파일이 노드 역할을 담지 않음"
  }
  assert {
    condition     = aws_iam_role_policy_attachments_exclusive.node.policy_arns == toset(["arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"])
    error_message = "관리 정책 목록이 변수와 다름"
  }
  assert {
    condition = jsondecode(aws_iam_role_policy.node_secrets_read.policy).Statement[0].Resource == [
      "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:ggeolmuse/production-??????",
      "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:ggeolmuse/origin-tls-??????",
    ]
    error_message = "노드 역할의 시크릿 읽기가 두 시크릿으로 좁지 않음"
  }
  assert {
    condition     = aws_iam_role_policy.db_backup_write.role == "ggeolmuse-ec2-role"
    error_message = "덤프 쓰기 정책이 가져온 노드 역할에 있지 않음"
  }
}

run "tfstate_bucket" {
  command = plan

  assert {
    condition     = aws_s3_bucket.tfstate.bucket == "ggeolmuse-tfstate-apne2"
    error_message = "상태 버킷 이름이 backend 와 다름"
  }
  assert {
    condition     = aws_s3_bucket_versioning.tfstate.versioning_configuration[0].status == "Enabled"
    error_message = "상태 버킷 버전 관리가 꺼짐"
  }
  assert {
    condition     = one([for r in aws_s3_bucket_server_side_encryption_configuration.tfstate.rule : r.apply_server_side_encryption_by_default[0].sse_algorithm]) == "AES256"
    error_message = "상태 버킷 기본 암호화가 SSE-S3 가 아님"
  }
  assert {
    condition     = one([for r in aws_s3_bucket_server_side_encryption_configuration.tfstate.rule : r.bucket_key_enabled])
    error_message = "상태 버킷 암호화의 버킷 키가 운영 값(켬)과 다름"
  }
  assert {
    condition = alltrue([
      aws_s3_bucket_public_access_block.tfstate.block_public_acls,
      aws_s3_bucket_public_access_block.tfstate.block_public_policy,
      aws_s3_bucket_public_access_block.tfstate.ignore_public_acls,
      aws_s3_bucket_public_access_block.tfstate.restrict_public_buckets,
    ])
    error_message = "상태 버킷 퍼블릭 차단이 다 켜지지 않음"
  }
}

run "rds" {
  command = plan

  variables {
    rds_instance_id          = "ggeolmuse-postgres"
    rds_allocated_storage    = 20
    rds_storage_type         = "gp2"
    rds_storage_encrypted    = true
    rds_publicly_accessible  = false
    rds_subnet_group_name    = "default-vpc-0123456789abcdef0"
    rds_security_group_ids   = ["sg-0fedcba9876543210"]
    rds_parameter_group_name = "ggeolmuse-pg15"
  }

  assert {
    condition     = aws_db_instance.postgres[0].identifier == "ggeolmuse-postgres"
    error_message = "RDS 식별자가 변수와 다름"
  }
  assert {
    condition = (
      aws_db_instance.postgres[0].engine == "postgres" &&
      aws_db_instance.postgres[0].engine_version == "15" &&
      aws_db_instance.postgres[0].instance_class == "db.t4g.micro" &&
      aws_db_instance.postgres[0].multi_az == false
    )
    error_message = "엔진 · 버전 · 등급 · Single-AZ 가 운영(PostgreSQL 15 · db.t4g.micro)과 다름"
  }
  assert {
    condition = (
      aws_db_instance.postgres[0].max_allocated_storage == 1000 &&
      aws_db_instance.postgres[0].performance_insights_enabled &&
      aws_db_instance.postgres[0].copy_tags_to_snapshot
    )
    error_message = "저장 공간 자동 늘리기 · Performance Insights · 스냅숏 이름표 복사가 운영 값과 다름"
  }
  assert {
    condition     = aws_db_instance.postgres[0].storage_encrypted && aws_db_instance.postgres[0].allocated_storage == 20 && aws_db_instance.postgres[0].vpc_security_group_ids == toset(["sg-0fedcba9876543210"])
    error_message = "RDS 변수 값이 들어가지 않음"
  }
  assert {
    condition     = aws_db_instance.postgres[0].parameter_group_name == "ggeolmuse-pg15"
    error_message = "RDS 파라미터 그룹이 변수와 다름(복원 · 가져오기 뒤 차이를 못 잡음)"
  }
  assert {
    condition     = aws_db_instance.postgres[0].backup_retention_period == 7 && aws_db_instance.postgres[0].backup_window == "23:10-23:40"
    error_message = "자동 백업이 7일 · 가동 시간 안(08:10 ~ 08:40 KST)이 아님"
  }
  assert {
    condition     = aws_db_instance.postgres[0].deletion_protection
    error_message = "RDS 삭제 보호가 꺼짐"
  }
  assert {
    condition = (
      !aws_db_instance.postgres[0].skip_final_snapshot &&
      aws_db_instance.postgres[0].final_snapshot_identifier == "ggeolmuse-postgres-final" &&
      !aws_db_instance.postgres[0].delete_automated_backups
    )
    error_message = "지울 때 최종 스냅숏 · 자동 백업을 남기지 않음"
  }
}

run "rds_backup_window_outside_uptime" {
  command = plan

  variables {
    rds_instance_id          = "ggeolmuse-postgres"
    rds_allocated_storage    = 20
    rds_storage_type         = "gp2"
    rds_storage_encrypted    = true
    rds_publicly_accessible  = false
    rds_subnet_group_name    = "default-vpc-0123456789abcdef0"
    rds_security_group_ids   = ["sg-0fedcba9876543210"]
    rds_parameter_group_name = "ggeolmuse-pg15"
    rds_backup_window        = "15:00-15:30"
  }

  expect_failures = [var.rds_backup_window]
}

run "db_backup_bucket" {
  command = plan

  assert {
    condition     = aws_s3_bucket_versioning.db_backup.versioning_configuration[0].status == "Enabled"
    error_message = "덤프 버킷 버전 관리가 꺼져 있어 쓰기 권한으로 옛 덤프를 덮어씀"
  }
  assert {
    condition = anytrue([
      for r in aws_s3_bucket_lifecycle_configuration.db_backup.rule :
      r.status == "Enabled" && one(r.noncurrent_version_expiration[*].noncurrent_days) == 30
    ])
    error_message = "덮어쓴 옛 덤프를 30일 두지 않음"
  }
}

run "rds_values_missing" {
  command = plan

  variables {
    rds_instance_id = "ggeolmuse-postgres"
  }

  expect_failures = [aws_db_instance.postgres]
}

run "no_rds" {
  command = plan

  assert {
    condition     = length(aws_db_instance.postgres) == 0
    error_message = "rds_instance_id 가 비었는데 RDS 를 가져옴"
  }
}
