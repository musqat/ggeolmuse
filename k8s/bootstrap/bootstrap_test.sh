#!/usr/bin/env bash
# 노드 스크립트 테스트(컨트롤 플레인 설정 렌더 · 노드 준비 · 부품 설치의 고정 값, 노드 밖에서 돎)
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
fail=0
check() { if eval "$2"; then echo "ok    $1"; else echo "FAIL  $1"; fail=1; fi; }

# 컨트롤 플레인 설정 렌더(주석 안의 자리표시자 설명은 남아도 됨, out 은 check 의 eval 이 읽음)
# shellcheck disable=SC2034
if ! out="$(bash "$here/control-plane.sh" --render 10.0.0.1)"; then echo "FAIL  렌더 실패"; fail=1; fi
check "렌더가 남은 자리표시자 없이 끝남" '! grep -v "^[[:space:]]*#" <<<"$out" | grep -q "\${"'
check "API 서버 주소가 들어감" 'grep -q "10.0.0.1" <<<"$out"'
check "파드 대역이 versions.env 값" 'grep -q "10.42.0.0/16" <<<"$out"'

# 노드 준비 · 부품 설치
check "helm 버전 고정" 'grep -q "^HELM_VERSION=v" "$here/../versions.env" && grep -q "HELM_VERSION" "$here/node-prep.sh"'
check "helm 받은 파일의 체크섬 확인" 'grep -q "sha256sum -c" "$here/node-prep.sh"'
check "sudo kubectl 줄임말 k" 'grep -q "/usr/local/bin/k" "$here/control-plane.sh"'
check "grafana-admin 은 없을 때만 만듦" 'grep -q "get secret grafana-admin" "$here/addons.sh"'

exit "$fail"
