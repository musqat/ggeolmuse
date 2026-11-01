# 멀티 노드 클러스터(k8s_cluster_enabled 일 때만 생김)
module "k8s_cluster" {
  source = "./modules/k8s-cluster"
  count  = var.k8s_cluster_enabled ? 1 : 0

  project_name           = var.project_name
  environment            = var.environment
  subnet_id              = var.k8s_subnet_id
  ingress_prefix_list_id = var.k8s_ingress_prefix_list_id
  rds_security_group_id  = var.k8s_rds_security_group_id
  secret_arns = [
    for name in var.node_secret_names :
    "arn:aws:secretsmanager:${var.aws_region}:${data.aws_caller_identity.current.account_id}:secret:${name}-??????"
  ]
}
