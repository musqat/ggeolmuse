# PR 의 읽기 전용 plan 을 GitHub Actions 가 맡는 역할(.github/workflows/terraform-plan.yml, ci_plan_role_enabled 일 때만)

locals {
  ci_plan_enabled = var.ci_plan_role_enabled ? 1 : 0
  github_oidc     = "token.actions.githubusercontent.com"
}

# 계정에 공급자가 이미 있으면 github_oidc_provider_arn 의 공급자를 씀
resource "aws_iam_openid_connect_provider" "github" {
  count = var.ci_plan_role_enabled && var.github_oidc_provider_arn == "" ? 1 : 0

  url            = "https://${local.github_oidc}"
  client_id_list = ["sts.amazonaws.com"]

  tags = merge(local.core_tags, { Name = "github-actions" })
}

# 이 저장소의 pull_request 실행만
resource "aws_iam_role" "ci_plan" {
  count = local.ci_plan_enabled

  name        = "${var.project_name}-terraform-plan"
  description = "GitHub Actions read-only terraform plan"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = { Federated = coalesce(var.github_oidc_provider_arn, one(aws_iam_openid_connect_provider.github[*].arn)) }
        Action    = "sts:AssumeRoleWithWebIdentity"
        Condition = {
          StringEquals = {
            "${local.github_oidc}:aud" = "sts.amazonaws.com"
            "${local.github_oidc}:sub" = "repo:${var.github_repository}:pull_request"
          }
        }
      }
    ]
  })

  tags = merge(local.core_tags, { Name = "${var.project_name}-terraform-plan" })
}

# 이 루트가 쓰는 서비스의 Describe · Get · List 와 상태 파일 읽기만
resource "aws_iam_role_policy" "ci_plan" {
  count = local.ci_plan_enabled

  name = "${var.project_name}-terraform-plan-read"
  role = aws_iam_role.ci_plan[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "State"
        Effect   = "Allow"
        Action   = "s3:GetObject"
        Resource = "${aws_s3_bucket.tfstate.arn}/ggeolmuse/terraform.tfstate"
      },
      {
        Sid    = "Resources"
        Effect = "Allow"
        Action = [
          "ec2:Describe*",
          "ec2:GetManagedPrefixListEntries",
          "iam:Get*",
          "iam:List*",
          "rds:Describe*",
          "rds:ListTagsForResource",
          "s3:GetBucket*",
          "s3:GetAccelerateConfiguration",
          "s3:GetEncryptionConfiguration",
          "s3:GetLifecycleConfiguration",
          "s3:GetReplicationConfiguration",
          "s3:ListBucket",
          "scheduler:Get*",
          "scheduler:List*",
          "sns:Get*",
          "sns:List*",
          "cloudwatch:Describe*",
          "cloudwatch:ListTagsForResource",
          "events:Describe*",
          "events:List*",
        ]
        Resource = "*"
      }
    ]
  })
}
