output "node_ids" {
  description = "노드 이름 → 인스턴스 ID"
  value       = { for k, n in aws_instance.nodes : k => n.id }
}

output "node_private_ips" {
  description = "노드 이름 → 사설 IP(컨트롤 플레인 주소 · kubeadm join)"
  value       = { for k, n in aws_instance.nodes : k => n.private_ip }
}

output "ingress_dns_name" {
  description = "NLB 주소(Cloudflare DNS 가 가리킴)"
  value       = aws_lb.ingress.dns_name
}

output "node_role_name" {
  description = "노드 역할 이름(운영 terraform 의 db_backup_writer_roles 에 넣음)"
  value       = aws_iam_role.nodes.name
}
