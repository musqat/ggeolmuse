variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-2"
}

variable "ec2_instance_id" {
  description = "EC2 instance ID to attach IAM role"
  type        = string
}

variable "project_name" {
  description = "Project name"
  type        = string
  default     = "ggeolmuse"
}

variable "environment" {
  description = "Environment (dev/staging/production)"
  type        = string
  default     = "production"
}
