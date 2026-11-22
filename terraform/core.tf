# 운영 핵심 자원(RDS · 상태 버킷), 가져오는 블록은 imports.tf

locals {
  tfstate_bucket = "ggeolmuse-tfstate-apne2"

  core_tags = {
    Environment = var.environment
    ManagedBy   = "terraform"
  }

  rds_required = {
    rds_allocated_storage    = var.rds_allocated_storage
    rds_storage_type         = var.rds_storage_type
    rds_storage_encrypted    = var.rds_storage_encrypted
    rds_publicly_accessible  = var.rds_publicly_accessible
    rds_subnet_group_name    = var.rds_subnet_group_name
    rds_security_group_ids   = var.rds_security_group_ids
    rds_parameter_group_name = var.rds_parameter_group_name
  }
}

# --- RDS ---

resource "aws_db_instance" "postgres" {
  count = local.rds_resource

  identifier     = var.rds_instance_id
  engine         = "postgres"
  engine_version = var.rds_engine_version
  instance_class = var.rds_instance_class
  multi_az       = false
  username       = var.rds_master_username

  allocated_storage      = var.rds_allocated_storage
  max_allocated_storage  = var.rds_max_allocated_storage
  storage_type           = var.rds_storage_type
  storage_encrypted      = var.rds_storage_encrypted
  publicly_accessible    = var.rds_publicly_accessible
  db_subnet_group_name   = var.rds_subnet_group_name
  vpc_security_group_ids = var.rds_security_group_ids
  parameter_group_name   = var.rds_parameter_group_name

  performance_insights_enabled = var.rds_performance_insights_enabled
  copy_tags_to_snapshot        = true

  # 시점 복원용 자동 백업
  backup_retention_period = var.rds_backup_retention_days
  backup_window           = var.rds_backup_window

  # 지우려면 삭제 보호부터 끄고, 지울 때도 최종 스냅숏 · 자동 백업을 남김
  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.rds_instance_id}-final"
  delete_automated_backups  = false

  tags = merge(local.core_tags, { Name = var.rds_instance_id })

  # 마스터 비밀번호는 코드 밖
  lifecycle {
    prevent_destroy = true
    ignore_changes  = [password]

    precondition {
      condition     = alltrue([for v in values(local.rds_required) : v != null])
      error_message = "RDS 를 가져오려면 값이 필요함: ${join(", ", [for k, v in local.rds_required : k if v == null])}"
    }
  }
}

# --- terraform 상태 버킷(backend 가 쓰는 버킷) ---

resource "aws_s3_bucket" "tfstate" {
  bucket = local.tfstate_bucket

  tags = merge(local.core_tags, { Name = local.tfstate_bucket })

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  rule {
    bucket_key_enabled = true

    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
