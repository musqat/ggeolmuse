# 멀티 노드 예비 클러스터 모듈(가짜 AWS 프로바이더)
mock_provider "aws" {
  mock_data "aws_ami" {
    defaults = { id = "ami-0123456789abcdef0" }
  }
  mock_data "aws_subnet" {
    defaults = { vpc_id = "vpc-0123456789abcdef0" }
  }
  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::123456789012:role/mock" }
  }
  mock_resource "aws_security_group" {
    defaults = { id = "sg-0aaaaaaaaaaaaaaaa" }
  }
  mock_resource "aws_lb" {
    defaults = { arn = "arn:aws:elasticloadbalancing:ap-northeast-2:123456789012:loadbalancer/net/mock/0123456789abcdef" }
  }
  mock_resource "aws_lb_target_group" {
    defaults = { arn = "arn:aws:elasticloadbalancing:ap-northeast-2:123456789012:targetgroup/mock/0123456789abcdef" }
  }
}

variables {
  project_name           = "ggeolmuse"
  environment            = "production"
  subnet_id              = "subnet-0123456789abcdef0"
  ingress_prefix_list_id = "pl-0123456789abcdef0"
  secret_arns            = ["arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:ggeolmuse/production-??????"]
}

run "nodes" {
  command = apply

  assert {
    condition     = toset([for n in aws_instance.nodes : n.subnet_id]) == toset(["subnet-0123456789abcdef0"]) && length(aws_instance.nodes) == 3
    error_message = "노드 셋이 한 서브넷(같은 AZ, EBS 볼륨이 AZ 에 묶임)"
  }

  assert {
    condition     = alltrue([for n in aws_instance.nodes : n.metadata_options[0].http_tokens == "required" && n.metadata_options[0].http_put_response_hop_limit == 2])
    error_message = "IMDSv2 만, 파드도 닿게 hop 2"
  }

  assert {
    condition     = alltrue([for n in aws_instance.nodes : strcontains(n.user_data, "k8s/bootstrap/node-prep.sh")])
    error_message = "노드가 기동 때 node-prep.sh 를 돌려야 함"
  }

  assert {
    condition     = alltrue([for n in aws_instance.nodes : n.source_dest_check == false])
    error_message = "노드의 출발지 · 목적지 검사 끔(Calico 가 같은 서브넷에서 파드 주소를 감싸지 않고 보냄)"
  }

  assert {
    condition     = alltrue([for n in aws_instance.nodes : n.associate_public_ip_address == true])
    error_message = "노드마다 자동 공인 IP(서브넷 설정과 상관없이, 켜진 동안만 과금 · NAT 없이 밖으로 나감)"
  }
}

run "ingress" {
  command = apply

  assert {
    condition     = aws_lb_listener.https.port == 443 && aws_lb_target_group.gateway.port == 30443
    error_message = "NLB 443 → Envoy Gateway https 노드 포트 30443"
  }

  assert {
    condition     = toset(keys(aws_lb_target_group_attachment.workers)) == toset(["worker-a", "worker-b"])
    error_message = "NLB 대상은 워커 둘"
  }

  assert {
    condition     = aws_vpc_security_group_ingress_rule.nlb_https.prefix_list_id == "pl-0123456789abcdef0" && aws_vpc_security_group_ingress_rule.nlb_https.from_port == 443
    error_message = "NLB 는 접두사 목록(Cloudflare)의 443 만"
  }

  assert {
    condition     = aws_vpc_security_group_ingress_rule.nlb_to_gateway.from_port == 30443 && aws_vpc_security_group_ingress_rule.nlb_to_gateway.to_port == 30443
    error_message = "노드는 NLB 에서 30443 만 받음"
  }
}

run "node_role" {
  command = apply

  assert {
    condition     = jsonencode(jsondecode(aws_iam_role_policy.secrets_read.policy).Statement[0].Resource) == jsonencode(["arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:ggeolmuse/production-??????"])
    error_message = "노드 역할은 받은 시크릿의 읽기만"
  }

  assert {
    condition     = toset([for a in aws_iam_role_policy_attachment.managed : a.policy_arn]) == toset(["arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore", "arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy"])
    error_message = "관리형 정책은 EBS CSI · SSM 둘(보안 그룹 수정 권한 없음)"
  }
}

run "rds_rule_only_with_group" {
  command = plan

  assert {
    condition     = length(aws_vpc_security_group_ingress_rule.rds_from_nodes) == 0
    error_message = "RDS 보안 그룹을 안 주면 규칙도 없음"
  }
}

run "rds_rule" {
  command = plan

  variables {
    rds_security_group_id = "sg-0bbbbbbbbbbbbbbbb"
  }

  assert {
    condition     = aws_vpc_security_group_ingress_rule.rds_from_nodes[0].security_group_id == "sg-0bbbbbbbbbbbbbbbb" && aws_vpc_security_group_ingress_rule.rds_from_nodes[0].from_port == 5432
    error_message = "RDS 보안 그룹에 노드 5432"
  }
}

run "control_plane_small_on_demand" {
  command = plan

  assert {
    condition     = aws_instance.nodes["control-plane"].instance_type == "t3a.small" && length(aws_instance.nodes["control-plane"].instance_market_options) == 0
    error_message = "컨트롤 플레인은 t3a.small 온디맨드"
  }
}

run "workers_persistent_spot" {
  command = plan

  assert {
    condition     = toset(keys(aws_instance.nodes)) == toset(["control-plane", "worker-a", "worker-b"])
    error_message = "노드는 컨트롤 플레인 · large 워커 둘"
  }
  assert {
    condition     = aws_instance.nodes["worker-a"].instance_type == "t3a.large" && aws_instance.nodes["worker-b"].instance_type == "t3.large"
    error_message = "워커 종류는 이름 → 종류 맵 그대로"
  }
  assert {
    condition = alltrue([for k in ["worker-a", "worker-b"] :
      aws_instance.nodes[k].instance_market_options[0].market_type == "spot"
      && aws_instance.nodes[k].instance_market_options[0].spot_options[0].spot_instance_type == "persistent"
      && aws_instance.nodes[k].instance_market_options[0].spot_options[0].instance_interruption_behavior == "stop"
    ])
    error_message = "워커는 유지 요청 스팟, 회수되면 멈춤(인스턴스 id · 디스크 그대로)"
  }
}

run "worker_names_exclude_control_plane" {
  command = plan

  variables {
    workers = { control-plane = "t3a.large" }
  }

  expect_failures = [var.workers]
}

run "node_role_name_for_backup" {
  command = plan

  assert {
    condition     = output.node_role_name == "ggeolmuse-k8s-node"
    error_message = "운영 terraform 의 백업 쓰기 역할 목록에 넣을 노드 역할 이름"
  }
}
