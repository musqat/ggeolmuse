# DB 논리 백업을 받는 S3 버킷과 EC2 쪽 쓰기 권한
#
# RDS 자동 스냅샷은 RDS 안에만 남는다. 계정 사고나 실수로 인스턴스를 지우면
# 같이 사라지고, 다른 DB 로 옮기거나 로컬에서 실데이터로 디버깅할 때도 못 쓴다.
# 그래서 k8s CronJob 이 매달 pg_dump 를 떠서 여기에 올린다.
# CronJob 은 helm/ggeolmuse 의 dbBackup 값으로 켠다.
#
# 인증은 EC2 인스턴스 프로파일이다. 파드가 IMDSv2 로 인스턴스 역할 자격증명을
# 받아 올린다. ESO 가 Secrets Manager 를 읽는 것과 같은 경로다.
#

data "aws_instance" "ggeolmuse" {
  instance_id = var.ec2_instance_id
}

data "aws_iam_instance_profile" "ggeolmuse" {
  name = data.aws_instance.ggeolmuse.iam_instance_profile
}

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

# 덤프는 월 1회라 보존 일수가 곧 보관 세대 수다. 400일이면 13개쯤 남는다.
# 실패해서 반쯤 올라간 멀티파트 조각은 하루 뒤 치운다.
resource "aws_s3_bucket_lifecycle_configuration" "db_backup" {
  bucket = aws_s3_bucket.db_backup.id

  rule {
    id     = "expire-old-dumps"
    status = "Enabled"

    filter {}

    expiration {
      days = var.db_backup_retention_days
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

# 쓰기만 준다. 읽기와 삭제는 노트북의 자격증명으로 한다.
# 파드가 털려도 기존 덤프를 지우거나 읽어갈 수는 없다.
resource "aws_iam_role_policy" "db_backup_write" {
  name = "${var.project_name}-db-backup-write"
  role = data.aws_iam_instance_profile.ggeolmuse.role_name

  policy = jsonencode({
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
