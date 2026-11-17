#!/usr/bin/env bash
# 운영 k3s 노드 세우기 · ArgoCD 설치(Amazon Linux, 사용: sudo bootstrap.sh [--dry-run], 버전은 K3S_VERSION 으로 바꿈)
set -euo pipefail

K3S_VERSION="${K3S_VERSION:-v1.33.5+k3s1}"
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"

usage() { echo "사용: sudo $0 [--dry-run]" >&2; }

dry_run=false
case "${1:-}" in
  --dry-run) dry_run=true ;;
  "") ;;
  *) usage; exit 2 ;;
esac

# k3s 가 이미 있거나 설정이 리포와 다른 노드에서는 멈춤(확인 기준 경로 BOOTSTRAP_SYSROOT)
sysroot="${BOOTSTRAP_SYSROOT:-}"
if [ -e "$sysroot/usr/local/bin/k3s" ] || [ -e "$sysroot/etc/systemd/system/k3s.service" ]; then
  echo "k3s 가 이미 있는 노드라 멈춤(새 노드 전용)" >&2
  exit 1
fi
if [ -e "$sysroot/etc/rancher/k3s/config.yaml" ] && ! cmp -s "$here/config.yaml" "$sysroot/etc/rancher/k3s/config.yaml"; then
  echo "/etc/rancher/k3s/config.yaml 이 리포 파일과 달라 멈춤" >&2
  exit 1
fi

if ! $dry_run && [ "$(id -u)" -ne 0 ]; then
  usage
  exit 1
fi
if ! $dry_run && ! command -v helm >/dev/null; then
  echo "helm 이 없음(ArgoCD 설치에 씀)" >&2
  exit 1
fi

# 명령 실행(--dry-run 이면 출력만)
run() {
  if $dry_run; then
    echo "+ $*"
  else
    "$@"
  fi
}

echo "k3s 설정 파일"
run install -d -m 755 /etc/rancher/k3s/config.yaml.d
run install -m 644 "$here/config.yaml" /etc/rancher/k3s/config.yaml
run install -m 644 "$here/kubelet-image-gc.yaml" /etc/rancher/k3s/config.yaml.d/kubelet-image-gc.yaml

echo "k3s ${K3S_VERSION}"
installer="<installer>"
$dry_run || installer="$(mktemp)"
run curl -sfL -o "$installer" https://get.k3s.io
run env INSTALL_K3S_VERSION="$K3S_VERSION" sh "$installer"
$dry_run || rm -f "$installer"
# 설치 직후에는 노드 객체가 없어 생길 때까지 기다림
if ! $dry_run; then
  for _ in $(seq 60); do
    k3s kubectl get nodes -o name 2>/dev/null | grep -q . && break
    sleep 5
  done
fi
run k3s kubectl wait --for=condition=Ready nodes --all --timeout=5m

# ArgoCD 설치와 부품(External Secrets · 모니터링 · Traefik 값) · 앱 Application 생성, 부품 동기화는 수동
echo "ArgoCD · Application"
run env -C "$root/helm/argocd" KUBECONFIG=/etc/rancher/k3s/k3s.yaml bash install-argocd.sh
run k3s kubectl apply -f "$root/helm/argocd/root.yaml"
run k3s kubectl apply -f "$root/helm/argocd/application-ggeolmuse.yaml"
