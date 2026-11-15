# 운영 핵심 자원(k3s EC2 · 보안 그룹 · 노드 역할 · RDS · 상태 버킷), 가져오는 블록은 imports.tf

locals {
  tfstate_bucket = "ggeolmuse-tfstate-apne2"

  core_tags = {
    Environment = var.environment
    ManagedBy   = "terraform"
  }

  # 노드 역할의 시크릿 읽기(이름 뒤 ?????? 는 Secrets Manager 가 더하는 6자)
  node_secrets_read_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret",
          "secretsmanager:GetResourcePolicy",
          "secretsmanager:ListSecretVersionIds",
        ]
        Resource = [
          for name in var.node_secret_names :
          "arn:aws:secretsmanager:${var.aws_region}:${data.aws_caller_identity.current.account_id}:secret:${name}-??????"
        ]
      }
    ]
  })

  rds_required = {
    rds_allocated_storage   = var.rds_allocated_storage
    rds_storage_type        = var.rds_storage_type
    rds_storage_encrypted   = var.rds_storage_encrypted
    rds_publicly_accessible = var.rds_publicly_accessible
    rds_subnet_group_name   = var.rds_subnet_group_name
    rds_security_group_ids  = var.rds_security_group_ids
  }
}

# --- EC2 ---

resource "aws_instance" "app" {
  ami                    = var.ec2_ami_id
  instance_type          = var.ec2_instance_type
  subnet_id              = var.ec2_subnet_id
  key_name               = var.ec2_key_name
  vpc_security_group_ids = concat([aws_security_group.app.id], var.ec2_extra_security_group_ids)
  iam_instance_profile   = aws_iam_instance_profile.node.name

  # IMDSv2 만(파드에서 노드 역할을 쓰게 hop 2)
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_size = var.ec2_root_volume_size
    volume_type = var.ec2_root_volume_type
  }

  tags = merge(local.core_tags, { Name = var.project_name })

  # 새 AMI · 키 페어로 바뀌어도 노드를 다시 만들지 않음(둘 다 만들 때만 쓰는 값)
  lifecycle {
    prevent_destroy = true
    ignore_changes  = [ami, key_name]
  }
}

# --- 보안 그룹 ---

data "aws_ec2_managed_prefix_list" "cloudflare" {
  name = var.cloudflare_prefix_list_name
}

# 규칙은 그룹 안에(그룹을 가져오면 규칙도 같이 들어옴)
resource "aws_security_group" "app" {
  name        = "${var.project_name}-sg"
  description = var.ec2_security_group_description
  vpc_id      = var.vpc_id

  # HTTPS 는 Cloudflare 대역만
  ingress {
    description     = var.cloudflare_rule_description
    protocol        = "tcp"
    from_port       = 443
    to_port         = 443
    prefix_list_ids = [data.aws_ec2_managed_prefix_list.cloudflare.id]
  }

  dynamic "ingress" {
    for_each = var.ec2_extra_ingress
    content {
      description = ingress.value.description
      protocol    = ingress.value.protocol
      from_port   = ingress.value.from_port
      to_port     = ingress.value.to_port
      cidr_blocks = ingress.value.cidr_blocks
    }
  }

  egress {
    protocol    = "-1"
    from_port   = 0
    to_port     = 0
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.core_tags, { Name = "${var.project_name}-sg" })

  lifecycle {
    prevent_destroy = true
  }
}

# --- 노드 역할 · 인스턴스 프로파일(EC2 에 연결된 것) ---

resource "aws_iam_role" "node" {
  name = var.node_role_name

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = { Service = "ec2.amazonaws.com" }
        Action    = "sts:AssumeRole"
      }
    ]
  })

  tags = merge(local.core_tags, { Name = var.node_role_name })

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_iam_instance_profile" "node" {
  name = var.node_instance_profile_name
  role = aws_iam_role.node.name

  tags = merge(local.core_tags, { Name = var.node_instance_profile_name })

  lifecycle {
    prevent_destroy = true
  }
}

# 노드 역할의 관리 정책 전체(목록에 없는 관리 정책은 역할에서 뺌)
resource "aws_iam_role_policy_attachments_exclusive" "node" {
  role_name   = aws_iam_role.node.name
  policy_arns = var.node_role_policy_arns
}

resource "aws_iam_role_policy" "node_secrets_read" {
  name   = "${var.project_name}-ec2-secrets-read"
  role   = aws_iam_role.node.id
  policy = local.node_secrets_read_policy
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
  storage_type           = var.rds_storage_type
  storage_encrypted      = var.rds_storage_encrypted
  publicly_accessible    = var.rds_publicly_accessible
  db_subnet_group_name   = var.rds_subnet_group_name
  vpc_security_group_ids = var.rds_security_group_ids

  tags = merge(local.core_tags, { Name = var.rds_instance_id })

  # 마스터 비밀번호는 코드 밖, 삭제 보호는 운영 값 그대로
  lifecycle {
    prevent_destroy = true
    ignore_changes  = [password, deletion_protection]

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
