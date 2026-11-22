variable "project_name" {
  description = "리소스 이름 앞에 오는 프로젝트 이름"
  type        = string
}

variable "environment" {
  description = "태그에 들어가는 환경 이름"
  type        = string
}

variable "subnet_id" {
  description = "노드 셋을 둘 공개 서브넷(EBS 볼륨이 AZ 에 묶여 한 AZ)"
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
  description = "컨트롤 플레인 인스턴스 타입(온디맨드, 앱은 안 올림)"
  type        = string
  default     = "t3a.medium"
}

variable "workers" {
  description = "워커 이름 → 인스턴스 타입(유지 요청 스팟, 회수되면 멈춤, 둘이 같이 회수되지 않게 종류를 섞음)"
  type        = map(string)
  default = {
    worker-a = "m5a.large"
    worker-b = "t3.large"
  }

  validation {
    condition     = length(var.workers) > 0 && !contains(keys(var.workers), "control-plane")
    error_message = "워커가 하나 이상이어야 하고 이름에 control-plane 은 쓸 수 없습니다."
  }
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
