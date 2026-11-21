output "node_ids" {
  description = "노드 이름 → 인스턴스 ID"
  value       = { for k, n in aws_instance.nodes : k => n.id }
}

output "node_private_ips" {
  description = "노드 이름 → 사설 IP(컨트롤 플레인 주소 · kubeadm join)"
  value       = { for k, n in aws_instance.nodes : k => n.private_ip }
}

output "node_role_name" {
  description = "노드 역할 이름(운영 terraform 의 db_backup_writer_roles 에 넣음)"
  value       = aws_iam_role.nodes.name
}
