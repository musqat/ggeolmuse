# 멀티 노드 kubeadm 클러스터(컨트롤 플레인 1 · 워커 N) · 노드 보안 그룹 · NLB

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
    { for i in range(var.worker_count) : "worker-${i + 1}" => { role = "worker", type = var.worker_instance_type } },
  )
  workers = { for k, n in local.nodes : k => n if n.role == "worker" }

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

# 노드끼리 전부(API 서버 · kubelet · etcd · Calico VXLAN · Typha · 컨트롤 플레인 지표)
resource "aws_vpc_security_group_ingress_rule" "node_to_node" {
  security_group_id            = aws_security_group.nodes.id
  referenced_security_group_id = aws_security_group.nodes.id
  ip_protocol                  = "-1"
}

resource "aws_vpc_security_group_ingress_rule" "nlb_to_traefik" {
  security_group_id            = aws_security_group.nodes.id
  referenced_security_group_id = aws_security_group.nlb.id
  ip_protocol                  = "tcp"
  from_port                    = 30443
  to_port                      = 30443
}

resource "aws_vpc_security_group_egress_rule" "nodes_out" {
  security_group_id = aws_security_group.nodes.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_security_group" "nlb" {
  name        = "${local.name}-nlb"
  description = "k8s ingress NLB"
  vpc_id      = data.aws_subnet.nodes.vpc_id
  tags        = merge(local.tags, { Name = "${local.name}-nlb" })
}

resource "aws_vpc_security_group_ingress_rule" "nlb_https" {
  security_group_id = aws_security_group.nlb.id
  prefix_list_id    = var.ingress_prefix_list_id
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "nlb_to_nodes" {
  security_group_id            = aws_security_group.nlb.id
  referenced_security_group_id = aws_security_group.nodes.id
  ip_protocol                  = "tcp"
  from_port                    = 30443
  to_port                      = 30443
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

  # IMDSv2 만(파드 External Secrets 가 노드 역할을 쓰게 hop 2)
  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
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

# --- NLB(TCP 통과, TLS 는 Traefik 의 Origin 인증서) ---

resource "aws_lb" "ingress" {
  name               = "${local.name}-ingress"
  load_balancer_type = "network"
  subnets            = [var.subnet_id]
  security_groups    = [aws_security_group.nlb.id]
  tags               = local.tags
}

resource "aws_lb_target_group" "traefik" {
  name        = "${local.name}-traefik"
  port        = 30443
  protocol    = "TCP"
  vpc_id      = data.aws_subnet.nodes.vpc_id
  target_type = "instance"

  health_check {
    protocol = "TCP"
    port     = "traffic-port"
  }

  tags = local.tags
}

resource "aws_lb_target_group_attachment" "workers" {
  for_each = local.workers

  target_group_arn = aws_lb_target_group.traefik.arn
  target_id        = aws_instance.nodes[each.key].id
  port             = 30443
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.ingress.arn
  port              = 443
  protocol          = "TCP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.traefik.arn
  }
}
