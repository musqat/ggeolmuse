variable "project_name" {
  description = "리소스 이름 앞에 붙는 프로젝트 이름"
  type        = string
}

variable "environment" {
  description = "태그에 들어가는 환경 이름"
  type        = string
}

variable "subnet_id" {
  description = "노드 셋 · NLB 를 둘 공개 서브넷(EBS 볼륨이 AZ 에 묶여 한 AZ)"
  type        = string
}

variable "ingress_prefix_list_id" {
  description = "NLB 443 을 받을 접두사 목록(Cloudflare)"
  type        = string
}

variable "rds_security_group_id" {
  description = "노드의 5432 를 받을 RDS 보안 그룹. 비면 규칙 없음"
  type        = string
  default     = ""
}

variable "secret_arns" {
  description = "노드 역할이 읽는 Secrets Manager 시크릿 ARN(External Secrets 가 씀)"
  type        = list(string)
}

variable "control_plane_instance_type" {
  description = "컨트롤 플레인 인스턴스 타입"
  type        = string
  default     = "t3a.medium"
}

variable "worker_instance_type" {
  description = "워커 인스턴스 타입"
  type        = string
  default     = "t3a.large"
}

variable "worker_count" {
  description = "워커 수"
  type        = number
  default     = 2
}

variable "root_volume_size" {
  description = "노드 루트 디스크 크기(GiB)"
  type        = number
  default     = 30
}

variable "repo_url" {
  description = "노드가 기동 때 받는 리포(k8s/bootstrap/node-prep.sh)"
  type        = string
  default     = "https://github.com/musqat/ggeolmuse.git"
}

variable "repo_ref" {
  description = "노드가 받는 리포 브랜치 · 태그"
  type        = string
  default     = "master"
}
