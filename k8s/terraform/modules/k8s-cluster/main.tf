# 멀티 노드 kubeadm 클러스터(컨트롤 플레인 온디맨드 1 · 스팟 워커) · 노드 보안 그룹

data "aws_subnet" "nodes" {
  id = var.subnet_id
}

data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }
}

locals {
  name = "${var.project_name}-k8s"
  tags = {
    Environment = var.environment
    ManagedBy   = "terraform"
  }

  nodes = merge(
    { "control-plane" = { role = "control-plane", type = var.control_plane_instance_type } },
    { for name, type in var.workers : name => { role = "worker", type = type } },
  )

  # 노드 공통 준비만(kubeadm init · join 은 k8s/README.md 순서대로 사람이)
  user_data = <<-EOT
    #!/bin/bash
    set -euxo pipefail
    dnf install -y git
    git clone --depth 1 --branch ${var.repo_ref} ${var.repo_url} /opt/ggeolmuse
    bash /opt/ggeolmuse/k8s/bootstrap/node-prep.sh
  EOT
}

# --- 보안 그룹 ---

resource "aws_security_group" "nodes" {
  name        = "${local.name}-nodes"
  description = "k8s nodes"
  vpc_id      = data.aws_subnet.nodes.vpc_id
  tags        = merge(local.tags, { Name = "${local.name}-nodes" })
}

# 들어오는 것은 노드끼리만(API 서버 · kubelet · etcd · Calico VXLAN · Typha · 컨트롤 플레인 지표), 밖에서 들어오는 포트 없음
resource "aws_vpc_security_group_ingress_rule" "node_to_node" {
  security_group_id            = aws_security_group.nodes.id
  referenced_security_group_id = aws_security_group.nodes.id
  ip_protocol                  = "-1"
}

resource "aws_vpc_security_group_egress_rule" "nodes_out" {
  security_group_id = aws_security_group.nodes.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_nodes" {
  count = var.rds_security_group_id == "" ? 0 : 1

  security_group_id            = var.rds_security_group_id
  referenced_security_group_id = aws_security_group.nodes.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}

# --- 노드 ---

resource "aws_instance" "nodes" {
  for_each = local.nodes

  ami                    = data.aws_ami.al2023.id
  instance_type          = each.value.type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [aws_security_group.nodes.id]
  iam_instance_profile   = aws_iam_instance_profile.nodes.name
  user_data              = local.user_data
  # 파드 주소를 출발지로 단 패킷 허용(Calico 가 같은 서브넷 안에서는 감싸지 않음)
  source_dest_check = false
  # 밖으로 나가는 연결용 공인 IP(서브넷 설정과 상관없이)
  associate_public_ip_address = true

  # 워커는 유지 요청 스팟(회수되면 멈췄다가 용량이 돌아오면 같은 인스턴스로 다시 뜸, 평일 스케줄이 그대로 켜고 끔)
  dynamic "instance_market_options" {
    for_each = each.value.role == "worker" ? [1] : []
    content {
      market_type = "spot"
      spot_options {
        spot_instance_type             = "persistent"
        instance_interruption_behavior = "stop"
      }
    }
  }

  # IMDSv2 만(파드 External Secrets 가 노드 역할을 쓰게 hop 2)
  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
    # 노드 준비가 EC2 이름표로 노드 레이블을 다는 인스턴스 태그 읽기
    instance_metadata_tags = "enabled"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_size
    encrypted   = true
  }

  # 새 AMI · 기동 스크립트가 바뀌어도 노드를 다시 만들지 않음
  lifecycle {
    ignore_changes = [ami, user_data]
  }

  tags = merge(local.tags, { Name = "${local.name}-${each.key}", Role = each.value.role })
}
