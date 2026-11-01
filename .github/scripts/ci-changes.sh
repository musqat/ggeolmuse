#!/usr/bin/env bash
# CI 이미지 빌드 대상 고르기 — master push 는 전부, PR 은 바뀐 폴더만(베이스 이미지 받기의 Docker Hub 횟수 제한)
set -euo pipefail

SERVICES=(config-server gateway-server user-service trade-service market-data-service backtest-service)
HEAD_SHA="${HEAD_SHA:-HEAD}"
OUT="${GITHUB_OUTPUT:-/dev/stdout}"

# PR 이면 master 에서 갈라진 뒤 PR 쪽에서 바뀐 파일 목록
all=false
files=""
if [ "${EVENT}" != "pull_request" ]; then
  all=true
else
  files=$(git diff --name-only "${BASE}...${HEAD_SHA}")
fi

# CI 설정 · 공용 액션 · 이 스크립트 · 메이븐 설정이 바뀌면 PR 도 전부 빌드(모든 잡에 영향)
if [ "$all" = false ] && grep -qE '^(\.github/workflows/ci\.yml|\.github/actions/|\.github/scripts/ci-changes\.sh|\.mvn/)' <<<"$files"; then
  all=true
fi

# 서비스는 자기 폴더가 바뀔 때 빌드(BOM · common · messaging · backend/pom.xml 이 바뀌면 백엔드 서비스 전부)
picked=()
for s in "${SERVICES[@]}"; do
  if [ "$all" = true ] || grep -qE "^backend/(${s}/|ggeolmuse-bom/|ggeolmuse-common/|messaging/|pom\.xml$)" <<<"$files"; then
    picked+=("\"$s\"")
  fi
done
services="[$(IFS=,; echo "${picked[*]-}")]"

# 프론트 · 채팅은 자기 폴더가 바뀔 때만 빌드
frontend=$all
if grep -q '^frontend-web/' <<<"$files"; then frontend=true; fi
chat=$all
if grep -q '^backend/chat-service/' <<<"$files"; then chat=true; fi

{
  echo "services=$services"
  echo "frontend=$frontend"
  echo "chat=$chat"
} >> "$OUT"
echo "event=${EVENT} all=$all services=$services frontend=$frontend chat=$chat" >&2
