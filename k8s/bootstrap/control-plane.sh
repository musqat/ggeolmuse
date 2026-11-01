#!/usr/bin/env bash
# 컨트롤 플레인 kubeadm init 뒤 워커 join 명령 출력(사용: sudo control-plane.sh [API 서버 주소])
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
set -a; . "$here/../versions.env"; set +a
endpoint="${1:-$(hostname -I | awk '{print $1}')}"

config="$(mktemp)"
sed -e "s|\${KUBERNETES_VERSION}|${KUBERNETES_VERSION}|g" \
    -e "s|\${CONTROL_PLANE_ENDPOINT}|${endpoint}|g" \
    -e "s|\${POD_CIDR}|${POD_CIDR}|g" \
    -e "s|\${SERVICE_CIDR}|${SERVICE_CIDR}|g" \
    "$here/kubeadm-config.yaml" > "$config"
grep -q '\${' "$config" && { echo "바뀌지 않은 자리표시자 있음" >&2; exit 1; }

kubeadm init --config "$config"
rm -f "$config"

# ec2-user 의 kubectl
install -d -o ec2-user -g ec2-user /home/ec2-user/.kube
install -m 600 -o ec2-user -g ec2-user /etc/kubernetes/admin.conf /home/ec2-user/.kube/config

echo "워커마다 sudo 로 실행:"
kubeadm token create --print-join-command
