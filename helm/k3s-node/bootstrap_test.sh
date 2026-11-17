#!/usr/bin/env bash
# bootstrap.sh 의 --dry-run 출력 테스트(노드 · 루트 권한 없이, 사용: bash bootstrap_test.sh)
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
failed=0

fail() { echo "FAIL  $*"; failed=1; }

# 기존 설치 확인 기준 경로(빈 폴더 = 새 노드)
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
export BOOTSTRAP_SYSROOT="$tmp/new"
mkdir -p "$BOOTSTRAP_SYSROOT"

# 출력에 기대한 줄이 있는지 확인
has() {
  grep -qxF -- "$2" <<<"$1" || fail "줄 없음: $2"
}

out="$(bash "$here/bootstrap.sh" --dry-run 2>&1)" || fail "--dry-run 종료 코드 $?"

has "$out" "+ install -d -m 755 /etc/rancher/k3s/config.yaml.d"
has "$out" "+ install -m 644 $here/config.yaml /etc/rancher/k3s/config.yaml"
has "$out" "+ install -m 644 $here/kubelet-image-gc.yaml /etc/rancher/k3s/config.yaml.d/kubelet-image-gc.yaml"
has "$out" "+ curl -sfL -o <installer> https://get.k3s.io"
has "$out" "+ env INSTALL_K3S_VERSION=v1.33.5+k3s1 sh <installer>"
has "$out" "+ k3s kubectl wait --for=condition=Ready nodes --all --timeout=5m"

# 관리자 kubeconfig 는 복사본 없이 root · ec2-user 그룹만 읽음
if grep -q '\.kube' <<<"$out"; then fail "ec2-user kubeconfig 복사본을 만듦"; fi
conf="$(grep -v '^[[:space:]]*#' "$here/config.yaml")"
has "$conf" 'write-kubeconfig-mode: "0640"'
has "$conf" "write-kubeconfig-group: ec2-user"

# k3s 다음에 ArgoCD 와 Application(부품 묶음 · 앱)
root="$(cd "$here/../.." && pwd)"
has "$out" "+ env -C $root/helm/argocd KUBECONFIG=/etc/rancher/k3s/k3s.yaml bash install-argocd.sh"
has "$out" "+ k3s kubectl apply -f $root/helm/argocd/root.yaml"
has "$out" "+ k3s kubectl apply -f $root/helm/argocd/application-ggeolmuse.yaml"
for f in helm/argocd/install-argocd.sh helm/argocd/root.yaml helm/argocd/application-ggeolmuse.yaml; do
  [ -f "$root/$f" ] || fail "파일 없음: $f"
done
ready_line=$(grep -n -- "condition=Ready" <<<"$out" | head -1 | cut -d: -f1)
argocd_line=$(grep -n -- "install-argocd.sh" <<<"$out" | head -1 | cut -d: -f1)
if [ -z "$ready_line" ] || [ -z "$argocd_line" ] || [ "$ready_line" -gt "$argocd_line" ]; then
  fail "노드 Ready 전에 ArgoCD 설치"
fi

# 설정 파일 설치 뒤 k3s 설치(첫 기동에서 설정을 읽음)
config_line=$(grep -n -- "/etc/rancher/k3s/config.yaml$" <<<"$out" | head -1 | cut -d: -f1)
install_line=$(grep -n -- "INSTALL_K3S_VERSION" <<<"$out" | head -1 | cut -d: -f1)
if [ -z "$config_line" ] || [ -z "$install_line" ] || [ "$config_line" -gt "$install_line" ]; then
  fail "설정 파일보다 설치가 먼저"
fi

# 운영 노드에 없는 끄기 옵션이 들어가지 않음(번들 Traefik · ServiceLB · local-path 를 씀)
if grep -v '^[[:space:]]*#' "$here/config.yaml" | grep -q disable; then
  fail "config.yaml 에 disable 옵션"
fi

# 버전을 바꿔 부를 수 있음
out="$(K3S_VERSION=v1.32.1+k3s1 bash "$here/bootstrap.sh" --dry-run 2>&1)"
has "$out" "+ env INSTALL_K3S_VERSION=v1.32.1+k3s1 sh <installer>"

# 모르는 인자는 종료 코드 2
bash "$here/bootstrap.sh" --nope >/dev/null 2>&1
code=$?
[ "$code" -eq 2 ] || fail "모르는 인자 종료 코드 $code, 기대 2"

# 실제 실행은 루트만
if [ "$(id -u)" -ne 0 ]; then
  bash "$here/bootstrap.sh" >/dev/null 2>&1
  code=$?
  [ "$code" -eq 1 ] || fail "루트 아닌 실제 실행 종료 코드 $code, 기대 1"
fi

# k3s 가 이미 있거나 기존 config.yaml 이 리포와 다르면 아무것도 안 하고 멈춤
stopped() {
  local sysroot="$1" what="$2" out code
  out="$(BOOTSTRAP_SYSROOT="$sysroot" bash "$here/bootstrap.sh" --dry-run 2>&1)"
  code=$?
  [ "$code" -eq 1 ] || fail "$what: 종료 코드 $code, 기대 1"
  grep -q "멈춤" <<<"$out" || fail "$what: 멈춘 까닭 없음"
  if grep -q '^+ ' <<<"$out"; then fail "$what: 멈추기 전에 할 일을 출력"; fi
}
mkdir -p "$tmp/bin/usr/local/bin" && touch "$tmp/bin/usr/local/bin/k3s"
stopped "$tmp/bin" "k3s 실행 파일이 있는 노드"
mkdir -p "$tmp/unit/etc/systemd/system" && touch "$tmp/unit/etc/systemd/system/k3s.service"
stopped "$tmp/unit" "k3s 유닛이 있는 노드"
mkdir -p "$tmp/conf/etc/rancher/k3s" && echo "tls-san: node.example" >"$tmp/conf/etc/rancher/k3s/config.yaml"
stopped "$tmp/conf" "리포와 다른 config.yaml"

# 리포와 같은 config.yaml 만 있으면 그대로 진행
mkdir -p "$tmp/same/etc/rancher/k3s" && cp "$here/config.yaml" "$tmp/same/etc/rancher/k3s/config.yaml"
out="$(BOOTSTRAP_SYSROOT="$tmp/same" bash "$here/bootstrap.sh" --dry-run 2>&1)" || fail "리포와 같은 config.yaml: 종료 코드 $?"
has "$out" "+ env INSTALL_K3S_VERSION=v1.33.5+k3s1 sh <installer>"

[ "$failed" -eq 0 ] && echo "ok  bootstrap.sh --dry-run"
exit "$failed"
