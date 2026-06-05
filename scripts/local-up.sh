#!/usr/bin/env bash
#
# ggeolmuse 로컬 온보딩 (docker-compose).
#   git clone 직후 한 번에 빌드 -> 기동 -> 시드 admin 로그인까지.
#
# 사용법:
#   bash scripts/local-up.sh                 # 빌드 + 기동
#   OPENAI_API_KEY=sk-... bash scripts/local-up.sh   # AI 챗봇까지
#   bash scripts/local-up.sh down            # 종료
#
# 전제: Docker Desktop 실행 중.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_DIR="$ROOT_DIR/docker-compose"
ENV_FILE="$COMPOSE_DIR/.env"
ENV_EXAMPLE="$COMPOSE_DIR/.env.example"

info()  { printf '\033[1;34m[*]\033[0m %s\n' "$*"; }
ok()    { printf '\033[1;32m[+]\033[0m %s\n' "$*"; }
warn()  { printf '\033[1;33m[!]\033[0m %s\n' "$*"; }
die()   { printf '\033[1;31m[x]\033[0m %s\n' "$*" >&2; exit 1; }

# docker compose v2(plugin) / v1 호환
compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  else
    docker-compose "$@"
  fi
}

cd "$COMPOSE_DIR"

# ── down 단축 ────────────────────────────────────────────────
if [[ "${1:-}" == "down" ]]; then
  info "스택 종료..."
  compose --profile ai down
  ok "종료 완료"
  exit 0
fi

# ── 1. 전제 체크 ─────────────────────────────────────────────
command -v docker >/dev/null 2>&1 || die "docker 없음. Docker Desktop 설치 후 실행."
docker info >/dev/null 2>&1 || die "Docker 데몬 미실행. Docker Desktop을 켜세요."
ok "docker 준비됨"

# ── 2. .env 준비 ─────────────────────────────────────────────
if [[ ! -f "$ENV_FILE" ]]; then
  cp "$ENV_EXAMPLE" "$ENV_FILE"
  ok ".env 생성됨 (기본값)."
else
  info ".env 이미 존재 — 보존"
fi

# OpenAI 키: 환경변수 우선, 없으면 .env 확인
OPENAI_KEY="${OPENAI_API_KEY:-}"
if [[ -z "$OPENAI_KEY" ]]; then
  OPENAI_KEY="$(grep -E '^OPENAI_API_KEY=' "$ENV_FILE" | head -1 | cut -d= -f2- || true)"
fi
export OPENAI_API_KEY="$OPENAI_KEY"

PROFILE_ARGS=()
if [[ -n "$OPENAI_KEY" ]]; then
  PROFILE_ARGS=(--profile ai)
  ok "OpenAI 키 감지 → chat-service 포함"
else
  warn "OPENAI_API_KEY 없음 → chat-service 제외. (AI 버튼은 보이나 호출 시 안내)"
fi

# ── 3. 빌드 + 기동 ───────────────────────────────────────────
info "이미지 빌드 + 기동 (처음엔 수 분 소요)..."
compose "${PROFILE_ARGS[@]}" up --build -d || die "기동 실패 (위 로그 확인)"
ok "기동 완료"

# ── 4. 안내 ──────────────────────────────────────────────────
cat <<EOF

────────────────────────────────────────────────
 컨테이너 상태:
   cd docker-compose && docker compose ps
   docker compose logs -f user-service   # 시드 admin 생성 로그

 접속:   http://localhost:3000

 시드 admin 로그인 (메일 인증 불필요):
   이메일:   admin@test.com
   비밀번호: Admin123!

 AI 챗봇: $([[ -n "$OPENAI_KEY" ]] && echo "활성" || echo "비활성 (OPENAI_API_KEY 주고 재실행 시 켜짐)")
 종료:   bash scripts/local-up.sh down
────────────────────────────────────────────────
EOF
