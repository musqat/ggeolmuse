# 콘솔에서 만든 운영 자원을 상태로 가져오는 블록(이미 가져온 자원에는 아무 일도 안 함)

import {
  to = aws_instance.app
  id = var.ec2_instance_id
}

import {
  to = aws_security_group.app
  id = var.ec2_security_group_id
}

import {
  to = aws_iam_role.node
  id = var.node_role_name
}

import {
  to = aws_iam_instance_profile.node
  id = var.node_instance_profile_name
}

import {
  to = aws_iam_role_policy_attachments_exclusive.node
  id = var.node_role_name
}

import {
  for_each = local.rds_enabled ? toset([var.rds_instance_id]) : toset([])
  to       = aws_db_instance.postgres[0]
  id       = each.value
}

import {
  to = aws_s3_bucket.tfstate
  id = local.tfstate_bucket
}

import {
  to = aws_s3_bucket_versioning.tfstate
  id = local.tfstate_bucket
}

import {
  to = aws_s3_bucket_server_side_encryption_configuration.tfstate
  id = local.tfstate_bucket
}

import {
  to = aws_s3_bucket_public_access_block.tfstate
  id = local.tfstate_bucket
}
