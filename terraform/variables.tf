variable "aws_region" {
  description = "리소스를 만들 AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "ec2_instance_id" {
  description = "가져올 운영 EC2 인스턴스 ID (스케줄 · 알람 대상)"
  type        = string
}

variable "project_name" {
  description = "리소스 이름 앞에 오는 프로젝트 이름"
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

# --- 운영 핵심 자원 (core.tf · imports.tf, 값은 README 의 가져오기 절) ---

variable "ec2_ami_id" {
  description = "EC2 AMI ID. 새로 만들 때만 쓰고 바뀌어도 교체하지 않는다"
  type        = string
}

variable "ec2_instance_type" {
  description = "EC2 인스턴스 종류"
  type        = string
  default     = "t3a.xlarge"
}

variable "ec2_subnet_id" {
  description = "EC2 서브넷 ID. 다르면 교체가 잡힌다"
  type        = string
}

variable "ec2_key_name" {
  description = "EC2 키 페어 이름(없으면 null). 새로 만들 때만 쓴다"
  type        = string
  nullable    = true
}

variable "ec2_root_volume_size" {
  description = "EC2 루트 볼륨 크기(GiB). 줄일 수 없다"
  type        = number
}

variable "ec2_root_volume_type" {
  description = "EC2 루트 볼륨 종류 (gp2 / gp3)"
  type        = string
}

variable "vpc_id" {
  description = "EC2 보안 그룹의 VPC ID. 다르면 교체가 잡힌다"
  type        = string
}

variable "ec2_security_group_id" {
  description = "가져올 EC2 보안 그룹 ID (ggeolmuse-sg)"
  type        = string
}

variable "ec2_security_group_description" {
  description = "EC2 보안 그룹 설명. 다르면 교체가 잡힌다"
  type        = string
}

variable "cloudflare_prefix_list_name" {
  description = "443 을 받을 Cloudflare IPv4 접두사 목록 이름"
  type        = string
  default     = "cloudflare-ipv4"
}

variable "cloudflare_rule_description" {
  description = "443 규칙의 설명"
  type        = string
  default     = ""
}

variable "ec2_extra_ingress" {
  description = "443 말고 보안 그룹에 둘 인바운드 규칙(SSH 등, 없으면 [])"
  type = list(object({
    description = string
    protocol    = string
    from_port   = number
    to_port     = number
    cidr_blocks = list(string)
  }))
}

variable "ec2_extra_security_group_ids" {
  description = "ggeolmuse-sg 말고 EC2 에 달린 보안 그룹 ID(없으면 []). 목록에 없는 그룹은 EC2 에서 빠진다"
  type        = list(string)
  default     = []
}

variable "node_role_name" {
  description = "EC2 에 연결된 노드 역할 이름"
  type        = string
  default     = "ggeolmuse-ec2-role"
}

variable "node_instance_profile_name" {
  description = "EC2 에 연결된 인스턴스 프로파일 이름"
  type        = string
  default     = "ggeolmuse-ec2-role"
}

variable "node_role_description" {
  description = "노드 역할 설명(콘솔에서 만들 때 넣은 값)"
  type        = string
  default     = "Allows EC2 instances to ggeolmuse"
}

variable "node_role_policy_arns" {
  description = "노드 역할에 둘 관리 정책 ARN 전체. 목록에 없는 관리 정책은 역할에서 빠진다"
  type        = set(string)
}

variable "rds_engine_version" {
  description = "RDS PostgreSQL 버전(주 버전만 적으면 부 버전 자동 올림을 따라감)"
  type        = string
  default     = "15"
}

variable "rds_instance_class" {
  description = "RDS 인스턴스 등급"
  type        = string
  default     = "db.t4g.micro"
}

variable "rds_master_username" {
  description = "RDS 마스터 사용자 이름. 다르면 교체가 잡힌다"
  type        = string
  default     = "postgres"
}

variable "rds_allocated_storage" {
  description = "RDS 할당 저장 용량(GiB). rds_instance_id 가 있으면 필요"
  type        = number
  default     = null
}

variable "rds_storage_type" {
  description = "RDS 저장소 종류 (gp2 / gp3). rds_instance_id 가 있으면 필요"
  type        = string
  default     = null
}

variable "rds_storage_encrypted" {
  description = "RDS 저장소 암호화 여부. 다르면 교체가 잡힌다. rds_instance_id 가 있으면 필요"
  type        = bool
  default     = null
}

variable "rds_publicly_accessible" {
  description = "RDS 퍼블릭 접근 여부. rds_instance_id 가 있으면 필요"
  type        = bool
  default     = null
}

variable "rds_subnet_group_name" {
  description = "RDS 서브넷 그룹 이름. rds_instance_id 가 있으면 필요"
  type        = string
  default     = null
}

variable "rds_security_group_ids" {
  description = "RDS 에 연결된 보안 그룹 ID. rds_instance_id 가 있으면 필요"
  type        = set(string)
  default     = null
}

variable "rds_parameter_group_name" {
  description = "RDS 파라미터 그룹 이름. rds_instance_id 가 있으면 필요"
  type        = string
  default     = null
}

variable "rds_max_allocated_storage" {
  description = "RDS 저장 공간 자동 늘리기 상한(GB, 0 이면 끔)"
  type        = number
  default     = 1000
}

variable "rds_performance_insights_enabled" {
  description = "RDS Performance Insights(무료 보관 7일)"
  type        = bool
  default     = true
}

variable "rds_backup_retention_days" {
  description = "RDS 자동 백업 보존 일수(시점 복원 범위)"
  type        = number
  default     = 7

  validation {
    condition     = var.rds_backup_retention_days >= 1 && var.rds_backup_retention_days <= 35
    error_message = "rds_backup_retention_days는 1~35 사이여야 합니다(0 이면 자동 백업이 꺼짐)."
  }
}

variable "rds_backup_window" {
  description = "RDS 자동 백업 창(UTC, HH:MM-HH:MM, 한국 시간으로 RDS 가동 시간 안)"
  type        = string
  default     = "23:10-23:40"

  # 백업 창의 시작 · 끝을 한국 시간(UTC+9)으로 바꿔 RDS 시작 ~ 중지 사이인지 확인
  validation {
    condition = try(
      regex("^([01][0-9]|2[0-3]):[0-5][0-9]-([01][0-9]|2[0-3]):[0-5][0-9]$", var.rds_backup_window) != null &&
      (tonumber(substr(var.rds_backup_window, 0, 2)) * 60 + tonumber(substr(var.rds_backup_window, 3, 2)) + 540) % 1440 >= var.rds_start_hour * 60 + var.rds_start_minute &&
      (tonumber(substr(var.rds_backup_window, 6, 2)) * 60 + tonumber(substr(var.rds_backup_window, 9, 2)) + 540) % 1440 <= var.rds_stop_hour * 60 + var.rds_stop_minute &&
      (tonumber(substr(var.rds_backup_window, 0, 2)) * 60 + tonumber(substr(var.rds_backup_window, 3, 2)) + 540) % 1440 < (tonumber(substr(var.rds_backup_window, 6, 2)) * 60 + tonumber(substr(var.rds_backup_window, 9, 2)) + 540) % 1440,
      false
    )
    error_message = "rds_backup_window는 UTC HH:MM-HH:MM 이고 한국 시간으로 RDS 시작 ~ 중지 사이여야 합니다."
  }
}

variable "rds_alarm_cpu_percent" {
  description = "RDS CPU 알람 기준(5분 평균 %, 15분 지속)"
  type        = number
  default     = 80
}

variable "rds_alarm_free_storage_gib" {
  description = "RDS 남은 저장 공간 알람 기준(GiB)"
  type        = number
  default     = 2
}

variable "rds_alarm_connections" {
  description = "RDS 연결 수 알람 기준(5분 최댓값, 10분 지속, 최대 연결 수는 인스턴스 메모리로 정해짐)"
  type        = number
  default     = 70
}

# --- PR 의 읽기 전용 plan (ci-plan-role.tf) ---

variable "ci_plan_role_enabled" {
  description = "GitHub Actions 가 맡는 읽기 전용 plan 역할과 OIDC 공급자를 만들지 여부"
  type        = bool
  default     = false
}

variable "github_oidc_provider_arn" {
  description = "계정에 이미 있는 GitHub OIDC 공급자 ARN. 비우면 새로 만든다"
  type        = string
  default     = ""
}

variable "github_repository" {
  description = "plan 역할을 맡을 수 있는 GitHub 저장소 (소유자/이름)"
  type        = string
  default     = "musqat/ggeolmuse"
}

# --- EC2 스케줄러 ---

variable "scheduler_enabled" {
  description = "EC2 와 RDS 스케줄을 켤지 여부의 기본값. 끄면 스케줄이 DISABLED 로 만들어진다"
  type        = bool
  default     = true
}

variable "ec2_scheduler_enabled" {
  description = "EC2 평일 시작 · 중지와 가동 확인 스케줄을 켤지 여부(null 이면 scheduler_enabled)"
  type        = bool
  default     = null
}

variable "rds_scheduler_enabled" {
  description = "RDS 평일 시작 · 중지 스케줄을 켤지 여부(null 이면 scheduler_enabled)"
  type        = bool
  default     = null
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
  description = "RDS 인스턴스 식별자. 값이 없으면 RDS 를 가져오지 않고 스케줄러도 만들지 않는다"
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

variable "db_backup_noncurrent_days" {
  description = "덮어쓰이거나 만료된 옛 덤프 버전을 두는 일수"
  type        = number
  default     = 30
}

# EC2 종료 알림 (ec2-stop-alert.tf)

variable "slack_webhook_url" {
  description = "EC2 종료 알림 · 사이트 확인 알림을 받을 슬랙 웹훅. 값이 없으면 둘 다 만들지 않는다"
  type        = string
  default     = ""
  sensitive   = true

  validation {
    condition     = var.slack_webhook_url == "" || startswith(var.slack_webhook_url, "https://hooks.slack.com/")
    error_message = "slack_webhook_url은 비어 있거나 https://hooks.slack.com/ 으로 시작해야 합니다."
  }
}

# 가동 시간 사이트 확인 (uptime-check.tf)

variable "uptime_check_urls" {
  description = "가동 시간에 5분마다 AWS 에서 GET 으로 확인할 주소(이름 → https 주소). 비거나 slack_webhook_url 이 없으면 만들지 않는다"
  type        = map(string)
  default = {
    frontend = "https://ggeolmuse.com/"
    price    = "https://ggeolmuse.com/api/market/price/AAPL"
    auth     = "https://ggeolmuse.com/auth/realms/muscathan"
  }

  validation {
    condition     = alltrue([for name, url in var.uptime_check_urls : can(regex("^[a-z0-9-]+$", name)) && startswith(url, "https://")])
    error_message = "uptime_check_urls 의 이름은 소문자 · 숫자 · - 만, 주소는 https:// 로 시작하는 값만 받습니다."
  }
}
