# DB 논리 백업(k8s CronJob 이 매달 pg_dump)을 받는 S3 버킷과 EC2 쪽 쓰기 권한
# CronJob 은 helm/ggeolmuse 의 dbBackup 으로 켜고, 파드는 IMDSv2 로 인스턴스 역할 자격증명을 받아 올림

resource "aws_s3_bucket" "db_backup" {
  bucket = var.db_backup_bucket_name

  tags = {
    Name        = var.db_backup_bucket_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_s3_bucket_public_access_block" "db_backup" {
  bucket = aws_s3_bucket.db_backup.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "db_backup" {
  bucket = aws_s3_bucket.db_backup.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# 같은 키로 덮어써도 옛 덤프가 옛 버전으로 남음
resource "aws_s3_bucket_versioning" "db_backup" {
  bucket = aws_s3_bucket.db_backup.id

  versioning_configuration {
    status = "Enabled"
  }
}

# 보존 400일(월 1회 덤프라 13개쯤), 덮어쓰이거나 만료된 옛 버전은 30일 뒤, 실패한 멀티파트 조각은 하루 뒤 정리
resource "aws_s3_bucket_lifecycle_configuration" "db_backup" {
  bucket = aws_s3_bucket.db_backup.id

  rule {
    id     = "expire-old-dumps"
    status = "Enabled"

    filter {}

    expiration {
      days = var.db_backup_retention_days
    }

    noncurrent_version_expiration {
      noncurrent_days = var.db_backup_noncurrent_days
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }

  # 옛 버전이 다 지워진 뒤 남는 삭제 표시
  rule {
    id     = "remove-delete-markers"
    status = "Enabled"

    filter {}

    expiration {
      expired_object_delete_marker = true
    }
  }

  depends_on = [aws_s3_bucket_versioning.db_backup]
}

locals {
  db_backup_write_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "PutDumps"
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:AbortMultipartUpload"]
        Resource = "${aws_s3_bucket.db_backup.arn}/*"
      },
      {
        Sid      = "ListForVerify"
        Effect   = "Allow"
        Action   = ["s3:ListBucket"]
        Resource = aws_s3_bucket.db_backup.arn
      }
    ]
  })
}

# 쓰기만(읽기 · 삭제는 노트북 자격증명, 파드 권한을 뺏겨도 기존 덤프는 못 읽고 덮어써도 옛 버전이 남음)
resource "aws_iam_role_policy" "db_backup_write" {
  name = "${var.project_name}-db-backup-write"
  role = aws_iam_role.node.name

  policy = local.db_backup_write_policy
}

# k3s 노드 역할 말고 백업을 쓰는 역할(멀티 노드 노드 역할 등)
resource "aws_iam_role_policy" "db_backup_writer" {
  for_each = toset(var.db_backup_writer_roles)

  name   = "${var.project_name}-db-backup-write"
  role   = each.value
  policy = local.db_backup_write_policy
}
