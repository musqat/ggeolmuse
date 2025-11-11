output "iam_role_arn" {
  description = "ARN of the IAM role"
  value       = aws_iam_role.ggeolmuse_ec2_secrets_role.arn
}

output "iam_role_name" {
  description = "Name of the IAM role"
  value       = aws_iam_role.ggeolmuse_ec2_secrets_role.name
}

output "instance_profile_arn" {
  description = "ARN of the instance profile"
  value       = aws_iam_instance_profile.ggeolmuse_ec2_profile.arn
}

output "instance_profile_name" {
  description = "Name of the instance profile"
  value       = aws_iam_instance_profile.ggeolmuse_ec2_profile.name
}

output "ec2_instance_id" {
  description = "EC2 instance ID with IAM role attached"
  value       = var.ec2_instance_id
}
