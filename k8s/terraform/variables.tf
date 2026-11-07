variable "aws_region" {
  description = "리소스를 만들 AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "project_name" {
  description = "리소스 이름 앞에 오는 프로젝트 이름"
  type        = string
  default     = "ggeolmuse"
}

variable "environment" {
  description = "태그에 들어가는 환경 이름"
  type        = string
  default     = "production"
}

variable "node_secret_names" {
  description = "노드 역할이 읽는 Secrets Manager 시크릿 이름 (External Secrets 가 쓴다)"
  type        = list(string)
  default     = ["ggeolmuse/production", "ggeolmuse/origin-tls"]
}

variable "subnet_id" {
  description = "노드 · NLB 를 둘 공개 서브넷"
  type        = string

  validation {
    condition     = var.subnet_id != ""
    error_message = "subnet_id 가 있어야 합니다."
  }
}

variable "ingress_prefix_list_id" {
  description = "NLB 443 을 받을 접두사 목록(Cloudflare)"
  type        = string

  validation {
    condition     = var.ingress_prefix_list_id != ""
    error_message = "ingress_prefix_list_id 가 있어야 합니다."
  }
}

variable "rds_security_group_id" {
  description = "노드의 5432 를 받을 RDS 보안 그룹. 비면 규칙 없음"
  type        = string
  default     = ""
}
