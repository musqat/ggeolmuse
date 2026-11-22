# 운영 핵심 자원(RDS · 상태 버킷) import 뒤 plan
# 가짜 프로바이더는 import 를 못 해 가져오는 자원마다 override_resource
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
}

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
