#!/usr/bin/env bash
# 컨트롤 플레인 kubeadm init 뒤 워커 join 명령 출력(사용: sudo control-plane.sh [API 서버 주소], 설정만 보기: control-plane.sh --render [주소])
set -euo pipefail

render=false
if [ "${1:-}" = "--render" ]; then
  render=true
  shift
fi

here="$(cd "$(dirname "$0")" && pwd)"
set -a; . "$here/../versions.env"; set +a
endpoint="${1:-$(hostname -I | awk '{print $1}')}"

config="$(mktemp)"
sed -e "s|\${KUBERNETES_VERSION}|${KUBERNETES_VERSION}|g" \
    -e "s|\${CONTROL_PLANE_ENDPOINT}|${endpoint}|g" \
    -e "s|\${POD_CIDR}|${POD_CIDR}|g" \
    -e "s|\${SERVICE_CIDR}|${SERVICE_CIDR}|g" \
    "$here/kubeadm-config.yaml" > "$config"
# 주석 밖에 남은 자리표시자
if grep -v '^[[:space:]]*#' "$config" | grep -q '\${'; then
  echo "바뀌지 않은 자리표시자 있음" >&2
  rm -f "$config"
  exit 1
fi
if $render; then
  cat "$config"
  rm -f "$config"
  exit 0
fi

kubeadm init --config "$config"
rm -f "$config"

# ec2-user 의 kubectl
install -d -o ec2-user -g ec2-user /home/ec2-user/.kube
install -m 600 -o ec2-user -g ec2-user /etc/kubernetes/admin.conf /home/ec2-user/.kube/config

# 어느 셸 · 사용자에서나 쓰는 줄임말 k(admin.conf 를 sudo 로)
cat > /usr/local/bin/k <<'SH'
#!/usr/bin/env bash
exec sudo kubectl --kubeconfig /etc/kubernetes/admin.conf "$@"
SH
chmod 755 /usr/local/bin/k

echo "워커마다 sudo 로 실행:"
kubeadm token create --print-join-command
