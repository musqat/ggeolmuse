variable "aws_region" {
  description = "리소스를 만들 AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "ec2_instance_id" {
  description = "IAM 역할과 스케줄을 붙일 EC2 인스턴스 ID"
  type        = string
}

variable "project_name" {
  description = "리소스 이름 앞에 붙는 프로젝트 이름"
  type        = string
  default     = "ggeolmuse"
}

variable "environment" {
  description = "태그에 들어가는 환경 이름 (dev / staging / production)"
  type        = string
  default     = "production"
}

variable "node_secret_names" {
  description = "노드 역할이 읽는 Secrets Manager 시크릿 이름 (External Secrets 가 쓴다)"
  type        = list(string)
  default     = ["ggeolmuse/production", "ggeolmuse/origin-tls"]
}

# --- EC2 스케줄러 ---

variable "scheduler_enabled" {
  description = "EC2 와 RDS 스케줄을 켤지 여부. 끄면 스케줄이 DISABLED 로 만들어진다"
  type        = bool
  default     = true
}

variable "scheduler_timezone" {
  description = "스케줄 해석 기준 타임존 (IANA)"
  type        = string
  default     = "Asia/Seoul"
}

variable "scheduler_days" {
  description = "스케줄이 도는 요일 (cron 의 day-of-week 필드)"
  type        = string
  default     = "MON-FRI"
}

variable "scheduler_start_hour" {
  description = "EC2 시작 시각 (시)"
  type        = number
  default     = 7

  validation {
    condition     = var.scheduler_start_hour >= 0 && var.scheduler_start_hour <= 23
    error_message = "scheduler_start_hour는 0~23 사이여야 합니다."
  }
}

variable "scheduler_start_minute" {
  description = "EC2 시작 시각 (분)"
  type        = number
  default     = 0

  validation {
    condition     = var.scheduler_start_minute >= 0 && var.scheduler_start_minute <= 59
    error_message = "scheduler_start_minute는 0~59 사이여야 합니다."
  }
}

variable "scheduler_stop_hour" {
  description = "EC2 중지 시각 (시)"
  type        = number
  default     = 19

  validation {
    condition     = var.scheduler_stop_hour >= 0 && var.scheduler_stop_hour <= 23
    error_message = "scheduler_stop_hour는 0~23 사이여야 합니다."
  }
}

variable "scheduler_stop_minute" {
  description = "EC2 중지 시각 (분)"
  type        = number
  default     = 0

  validation {
    condition     = var.scheduler_stop_minute >= 0 && var.scheduler_stop_minute <= 59
    error_message = "scheduler_stop_minute는 0~59 사이여야 합니다."
  }
}

variable "alarm_email" {
  description = "알람 수신 이메일. 값이 없으면 구독을 만들지 않는다"
  type        = string
  default     = ""

  validation {
    condition     = var.alarm_email == "" || can(regex("^[^@]+@[^@]+[.][^@]+$", var.alarm_email))
    error_message = "alarm_email은 비어 있거나 올바른 이메일 형식이어야 합니다."
  }
}

# --- RDS 스케줄러 ---

variable "rds_instance_id" {
  description = "RDS 인스턴스 식별자. 값이 없으면 RDS 스케줄러를 만들지 않는다"
  type        = string
  default     = ""
}

variable "rds_start_hour" {
  description = "RDS 시작 시각 (시). EC2 보다 앞서야 한다"
  type        = number
  default     = 6

  validation {
    condition     = var.rds_start_hour >= 0 && var.rds_start_hour <= 23
    error_message = "rds_start_hour는 0~23 사이여야 합니다."
  }
}

variable "rds_start_minute" {
  description = "RDS 시작 시각 (분)"
  type        = number
  default     = 40

  validation {
    condition     = var.rds_start_minute >= 0 && var.rds_start_minute <= 59
    error_message = "rds_start_minute는 0~59 사이여야 합니다."
  }
}

variable "rds_stop_hour" {
  description = "RDS 중지 시각 (시). EC2 보다 뒤여야 한다"
  type        = number
  default     = 19

  validation {
    condition     = var.rds_stop_hour >= 0 && var.rds_stop_hour <= 23
    error_message = "rds_stop_hour는 0~23 사이여야 합니다."
  }
}

variable "rds_stop_minute" {
  description = "RDS 중지 시각 (분)"
  type        = number
  default     = 5

  validation {
    condition     = var.rds_stop_minute >= 0 && var.rds_stop_minute <= 59
    error_message = "rds_stop_minute는 0~59 사이여야 합니다."
  }
}

# DB 백업 (db-backup.tf)

variable "db_backup_bucket_name" {
  description = "pg_dump 를 올릴 S3 버킷 이름."
  type        = string
  default     = "ggeolmuse-db-backup-apne2"
}

variable "db_backup_retention_days" {
  description = "덤프 보존 일수."
  type        = number
  default     = 400
}

# EC2 종료 알림 (ec2-stop-alert.tf)

variable "slack_webhook_url" {
  description = "EC2 종료 알림을 받을 슬랙 웹훅. 값이 없으면 알림을 만들지 않는다"
  type        = string
  default     = ""
  sensitive   = true

  validation {
    condition     = var.slack_webhook_url == "" || startswith(var.slack_webhook_url, "https://hooks.slack.com/")
    error_message = "slack_webhook_url은 비어 있거나 https://hooks.slack.com/ 으로 시작해야 합니다."
  }
}

# --- 멀티 노드 예비 클러스터 (k8s-cluster.tf) ---

variable "k8s_cluster_enabled" {
  description = "멀티 노드 예비 클러스터를 만들지 여부. 기본은 만들지 않음"
  type        = bool
  default     = false
}

variable "k8s_subnet_id" {
  description = "멀티 노드 노드 · NLB 를 둘 공개 서브넷"
  type        = string
  default     = ""

  validation {
    condition     = !var.k8s_cluster_enabled || var.k8s_subnet_id != ""
    error_message = "k8s_cluster_enabled 이면 k8s_subnet_id 가 있어야 합니다."
  }
}

variable "k8s_ingress_prefix_list_id" {
  description = "멀티 노드 NLB 443 을 받을 접두사 목록(Cloudflare)"
  type        = string
  default     = ""

  validation {
    condition     = !var.k8s_cluster_enabled || var.k8s_ingress_prefix_list_id != ""
    error_message = "k8s_cluster_enabled 이면 k8s_ingress_prefix_list_id 가 있어야 합니다."
  }
}

variable "k8s_rds_security_group_id" {
  description = "멀티 노드 노드의 5432 를 받을 RDS 보안 그룹. 비면 규칙 없음"
  type        = string
  default     = ""
}
