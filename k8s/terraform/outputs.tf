output "node_ids" {
  description = "노드 이름 → 인스턴스 ID"
  value       = module.k8s_cluster.node_ids
}

output "ingress_dns_name" {
  description = "NLB 주소(Cloudflare DNS 를 여기로)"
  value       = module.k8s_cluster.ingress_dns_name
}

output "node_role_name" {
  description = "노드 역할 이름(운영 terraform 의 db_backup_writer_roles 에 넣음)"
  value       = module.k8s_cluster.node_role_name
}
