#!/usr/bin/env bash
# NetworkPolicy 허용 포트와 business 서비스의 containerPort 대조
#   bash .github/scripts/check-netpol-ports.sh rendered.yaml
set -euo pipefail

file=${1:-rendered.yaml}
tier="tier: business-service"
gateway="app: gateway-server"

# 게이트웨이 → business 정책의 허용 포트
allowed=$(awk -v tier="$tier" -v gw="$gateway" '
  /^---/            { kind=""; biz=0; rule=0; gwrule=0; next }
  /^kind: /         { kind=$2; next }
  kind!="NetworkPolicy" { next }
  $0 == "      " tier                   { biz=1 }   # podSelector 만
  /^  - from:/      { rule=1; gwrule=0; next }
  rule && /^          / && index($0, gw) { gwrule=1 }
  rule && gwrule && biz && /^      port: / { print $2 }
' "$file" | sort -un)

# business 워크로드의 containerPort
workloads=$(awk -v tier="$tier" '
  /^---/            { kind=""; name=""; biz=0; next }
  /^kind: /         { kind=$2; next }
  kind!="Deployment" && kind!="StatefulSet" { next }
  /^  name: / && name=="" { name=$2 }
  $0 == "        " tier            { biz=1 }   # template.labels 만
  /containerPort: /  { if (biz) print name, $NF }
' "$file" | sort -u)

if [ -z "$allowed" ]; then
  echo "FAIL  NetworkPolicy 허용 포트를 읽지 못함"
  exit 1
fi
if [ -z "$workloads" ]; then
  echo "FAIL  business 워크로드를 찾지 못함"
  exit 1
fi

echo "허용 포트  $(echo "$allowed" | tr '\n' ' ')"
fail=0
while read -r name port; do
  if echo "$allowed" | grep -qx "$port"; then
    echo "ok    $name $port"
  else
    echo "FAIL  $name $port · 허용 목록에 없음"
    fail=1
  fi
done <<< "$workloads"

exit $fail
