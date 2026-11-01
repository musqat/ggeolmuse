# 노드 역할(시크릿 읽기 · EBS 볼륨 CSI · SSM 접속, 보안 그룹을 바꾸는 권한 없음)

resource "aws_iam_role" "nodes" {
  name = "${local.name}-node"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = local.tags
}

resource "aws_iam_role_policy" "secrets_read" {
  name = "${local.name}-secrets-read"
  role = aws_iam_role.nodes.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret",
        "secretsmanager:GetResourcePolicy",
        "secretsmanager:ListSecretVersionIds",
      ]
      Resource = var.secret_arns
    }]
  })
}

resource "aws_iam_role_policy_attachment" "managed" {
  for_each = toset([
    "arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy",
    "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore",
  ])

  role       = aws_iam_role.nodes.name
  policy_arn = each.value
}

resource "aws_iam_instance_profile" "nodes" {
  name = "${local.name}-node"
  role = aws_iam_role.nodes.name
  tags = local.tags
}
