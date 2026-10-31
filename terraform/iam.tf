# IAM Role for EC2 to access AWS Secrets Manager
resource "aws_iam_role" "ggeolmuse_ec2_secrets_role" {
  name        = "ggeolmuse-ec2-secrets-role"
  description = "Role for EC2 to access AWS Secrets Manager"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = {
    Name        = "ggeolmuse-ec2-secrets-role"
    Environment = "production"
    ManagedBy   = "terraform"
  }
}

# 노드 역할은 두 시크릿의 읽기만. 이름 뒤 ?????? 는 Secrets Manager 가 붙이는 6자
resource "aws_iam_role_policy" "ec2_secrets_read" {
  name = "${var.project_name}-ec2-secrets-read"
  role = aws_iam_role.ggeolmuse_ec2_secrets_role.id

  policy = jsonencode({
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
}

# Instance Profile to attach the role to EC2
resource "aws_iam_instance_profile" "ggeolmuse_ec2_profile" {
  name = "ggeolmuse-ec2-profile"
  role = aws_iam_role.ggeolmuse_ec2_secrets_role.name

  tags = {
    Name        = "ggeolmuse-ec2-profile"
    Environment = "production"
    ManagedBy   = "terraform"
  }
}
