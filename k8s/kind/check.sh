#!/usr/bin/env bash
# kind 3노드 확인: 노드 분산 · NetworkPolicy · 게이트웨이 응답 · 워커 하나를 비울 때 응답
set -uo pipefail

base=http://127.0.0.1:18080
host="Host: ggeolmuse.localhost"
apps=(config-server gateway-server user-service trade-service market-data-service backtest-service chat-service frontend-web keycloak)
fail=0

ok()  { echo "OK   $*"; }
bad() { echo "FAIL $*"; fail=1; }
code() { curl -s -o /dev/null -w '%{http_code}' --max-time 5 -H "$host" "$base$1"; }

echo "== 노드 분산(레플리카 2 가 서로 다른 워커)"
for a in "${apps[@]}"; do
  nodes="$(kubectl get pods -l app="$a" --field-selector=status.phase=Running \
    -o jsonpath='{range .items[*]}{.spec.nodeName}{"\n"}{end}' | sort -u | grep -c .)"
  [ "$nodes" -eq 2 ] && ok "$a 노드 $nodes" || bad "$a 노드 $nodes"
done

echo "== NetworkPolicy(업무 서비스 포트는 게이트웨이 · 업무 서비스만)"
probe() {
  kubectl run "np-$1" --rm -i --restart=Never --quiet --image=curlimages/curl:8.11.1 --labels="$2" -- \
    curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://user-service:8080/ 2>/dev/null | tr -dc '0-9'
}
c="$(probe deny role=probe)"
[ "${c:-000}" = 000 ] && ok "레이블 없는 파드 → user-service 막힘" || bad "레이블 없는 파드 → user-service $c"
c="$(probe allow tier=business-service)"
[ "${c:-000}" != 000 ] && ok "업무 서비스 레이블 파드 → user-service $c" || bad "업무 서비스 레이블 파드 → user-service 막힘"

echo "== 게이트웨이 응답(Traefik 노드 포트)"
c="$(code /)"; [ "$c" = 200 ] && ok "/ $c" || bad "/ $c"
c="$(code /api/market/price/AAPL)"; [ "$c" != 000 ] && [ "$c" -lt 500 ] && ok "/api/market/price/AAPL $c" || bad "/api/market/price/AAPL $c"

echo "== 워커 하나 비우기(PDB 로 앱이 남는지)"
worker="$(kubectl get pods -l app=gateway-server -o jsonpath='{.items[0].spec.nodeName}')"
log="$(mktemp)"
( while :; do [ "$(code /)" = 200 ] || echo x; sleep 1; done ) > "$log" &
watcher=$!
kubectl drain "$worker" --ignore-daemonsets --delete-emptydir-data --timeout=10m >/dev/null
for a in "${apps[@]}"; do
  ready="$(kubectl get deploy "$a" -o jsonpath='{.status.readyReplicas}' 2>/dev/null)"
  [ "${ready:-0}" -ge 1 ] && ok "$worker 비운 뒤 $a 준비 ${ready}" || bad "$worker 비운 뒤 $a 준비 ${ready:-0}"
done
kill "$watcher" 2>/dev/null
errors="$(grep -c x "$log")"; rm -f "$log"
[ "$errors" -eq 0 ] && ok "비우는 동안 / 실패 0" || bad "비우는 동안 / 실패 $errors"
kubectl uncordon "$worker" >/dev/null
echo "($worker 다시 받음. 노드 디스크 볼륨을 쓰는 Kafka · Zookeeper 는 그 노드로 돌아와야 뜸)"

exit "$fail"
