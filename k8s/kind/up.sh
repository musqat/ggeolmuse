#!/usr/bin/env bash
# 로컬 kind 3노드에 부품 · 테스트 시크릿 · Postgres · 앱 차트를 올림(사용: bash k8s/kind/up.sh)
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
set -a; . "$root/k8s/versions.env"; set +a
cluster=ggeolmuse

for t in kind kubectl helm docker openssl; do
  command -v "$t" >/dev/null || { echo "$t 없음" >&2; exit 1; }
done
have="$(kind version | awk '{print $2}')"
[ "$have" = "$KIND_VERSION" ] || echo "kind $have(고정은 $KIND_VERSION, 노드 이미지가 안 맞으면 클러스터가 안 뜰 수 있음)" >&2

if ! kind get clusters | grep -qx "$cluster"; then
  kind create cluster --name "$cluster" --image "$KIND_NODE_IMAGE" --config "$here/cluster.yaml"
fi
kubectl config use-context "kind-$cluster"

bash "$root/k8s/bootstrap/addons.sh" kind

# 테스트용 값(화면 · 로그에 찍지 않음)
if ! kubectl get secret ggeolmuse-secrets >/dev/null 2>&1; then
  env_file="$(mktemp)"
  trap 'rm -f "$env_file"' EXIT
  chmod 600 "$env_file"
  rnd() { openssl rand -hex 16; }
  client="$(rnd)"
  db="jdbc:postgresql://postgres:5432"
  cat > "$env_file" <<VALUES
CONFIG_SERVER_USERNAME=config
CONFIG_SERVER_PASSWORD=$(rnd)
DB_USERNAME=ggeolmuse
DB_PASSWORD=$(rnd)
DB_URL_USER=$db/userdb
DB_URL_TRADE=$db/tradedb
DB_URL_MARKET=$db/marketdb
DB_URL_BACKTEST=$db/backtestdb
KEYCLOAK_SECRET=$client
KEYCLOAK_CLIENT_SECRET=$client
KEYCLOAK_ADMIN_USERNAME=admin
KEYCLOAK_ADMIN_PASSWORD=$(rnd)
KEYCLOAK_ADMIN_USER_PASSWORD=$(rnd)
REDIS_PASSWORD=$(rnd)
MAIL_USERNAME=kind@example.com
MAIL_PASSWORD=unused
MAIL_FROM=kind@example.com
KOREAEXIM_API_KEY=unused
ALPHAVANTAGE_API_KEY=unused
OPENAI_API_KEY=unused
SLACK_WEBHOOK_URL=
VALUES
  kubectl create secret generic ggeolmuse-secrets --from-env-file="$env_file" >/dev/null
  echo "ggeolmuse-secrets 만듦(키 $(grep -c = "$env_file")개)"
fi

kubectl apply -f "$here/postgres.yaml"
kubectl rollout status deployment/postgres --timeout=5m

v="$root/helm/ggeolmuse"
helm upgrade --install ggeolmuse "$v" \
  -f "$v/values-prod.yaml" -f "$v/values-k8s.yaml" -f "$v/values-kind.yaml" \
  --wait --timeout 30m

echo "http://ggeolmuse.localhost:18080 (확인: bash k8s/kind/check.sh)"
