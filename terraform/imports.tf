# 콘솔에서 만든 운영 자원을 상태로 가져오는 블록(이미 가져온 자원에는 아무 일도 안 함)

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
