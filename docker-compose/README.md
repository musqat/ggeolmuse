# Docker Compose

## 프로젝트에서의 사용

초기 개발 단계에서 로컬 환경 구성을 위해 사용했다.

```bash
docker-compose up -d  # 전체 스택 한 번에 실행
```

**선택한 이유:**
- 6개 마이크로서비스 + 인프라(Redis, Keycloak, Kafka)를 한 번에 실행 가능
- 서비스 간 의존성 자동 관리 (`depends_on`)
- 로컬 개발/테스트에 충분

**한계:**
- 단일 호스트만 지원 (서버 다운 시 전체 중단)
- 자동 복구/스케일링 없음
- 프로덕션 배포에 부적합

이후 프로덕션 요구사항(고가용성, 자동 스케일링)을 충족하기 위해 Kubernetes로 전환했다.

---

## 현재 상태

**사용 중:**
- 로컬 개발 환경
- CI/CD 통합 테스트

**사용 안 함:**
- 프로덕션 배포 (Kubernetes + Helm 사용)

---

## 서비스 구성

| 서비스 | 포트 | 역할 |
|--------|------|------|
| config-server | 8888 | 중앙 설정 관리 |
| gateway-server | 8070 | API Gateway |
| user-service | 8080 | 사용자 관리 |
| trade-service | 8081 | 거래 관리 |
| backtest-service | 8082 | 백테스팅 |
| market-data-service | 8083 | 시장 데이터 |
| keycloak | 7001 | 인증 서버 |
| redis | 6379 | 캐시 |

---

## 빠른 시작

```bash
cd docker-compose
docker-compose up -d              # 전체 시작
docker-compose logs -f user-service  # 로그 확인
docker-compose down               # 종료
```

---

## 다음 단계

Docker Compose의 한계를 극복하기 위해 **Kubernetes**로 전환
→ 자세한 내용: `../k8s/README.md`
