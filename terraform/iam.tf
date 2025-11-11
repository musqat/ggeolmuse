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

# Attach AWS managed policy for Secrets Manager access
resource "aws_iam_role_policy_attachment" "secrets_manager_read" {
  role       = aws_iam_role.ggeolmuse_ec2_secrets_role.name
  policy_arn = "arn:aws:iam::aws:policy/SecretsManagerReadWrite"
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
