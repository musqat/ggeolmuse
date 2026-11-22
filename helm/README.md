# 인프라 · 배포

## 인프라 구성

```
Cloudflare (DNS · 프록시 · 꺼져 있을 때 안내 Worker)
    │ 443, Cloudflare 대역만
AWS EC2 t3a.xlarge (16GB) — K3s 단일 노드
    ├── Traefik IngressRoute
    ├── ArgoCD (GitOps)
    ├── 관측: Prometheus · Grafana · AlertManager · Loki · Tempo (OpenTelemetry Java Agent)
    ├── 인프라 파드: Kafka + Zookeeper · Redis · Keycloak
    └── 앱 파드: gateway-server · config-server · user · trade · market-data · backtest · chat · frontend-web

AWS RDS db.t4g.micro (PostgreSQL 15, Single-AZ) — 서비스 DB 넷 + Keycloak
AWS Secrets Manager — 앱 · Origin 인증서 시크릿
AWS S3 — 월간 DB 덤프
```

## 구성 등급

아키텍처는 그대로 두고 운영 등급만 낮췄다.

| 구성 | 관리형으로 짜면 | 지금 |
|---|---|---|
| Kubernetes | EKS | K3s (EC2 단일 노드) |
| Database | RDS Multi-AZ | RDS Single-AZ |
| Cache | ElastiCache | 파드 안 Redis |
| Message Queue | MSK | 파드 안 Kafka |
| Load Balancer | ALB | Cloudflare 프록시 |

포기한 것은 고가용성 · 자동 스케일링 · 관리형 서비스 · 무중단 배포이고, 남긴 것은 마이크로서비스 구조 · 컨테이너 · 이벤트 기반 흐름 · 관측이다.

## 배포 흐름

```
PR — GitHub Actions: 서비스 빌드 · 테스트 · 커버리지 최소치, 이미지 빌드 → Trivy 스캔, E2E(docker compose)
    ↓ master 머지 뒤 CI · E2E 초록
릴리스 — PR 본문에서 체크한 서비스만 이미지 빌드 → Trivy → Docker Hub(muscathan/*) 푸시,
         릴리스 봇이 values-prod.yaml 의 이미지 태그를 master 에 커밋
    ↓
ArgoCD — master 의 helm/ggeolmuse 를 자동 동기화(지운 리소스는 지우고, 손으로 바꾼 것은 되돌림) → K3s
```

## 값 파일과 시크릿

| 파일 | 담는 것 |
|---|---|
| `values.yaml` | 공통(이미지 레지스트리 · 포트 · Kafka 토픽 · probe · 자원 기본값) |
| `values-dev.yaml` | 로컬(app.localhost · 표준 Ingress · dev 프로파일) |
| `values-prod.yaml` | 운영(ggeolmuse.com · IngressRoute · Secrets Manager · 서비스별 이미지 태그 · 자원) |
| `values-secret.yaml` | 로컬 시크릿(git 에 없음, `.example` 만) |

운영 시크릿은 Secrets Manager `ggeolmuse/production` 을 External Secrets 가 `ggeolmuse-secrets` 로 내려준다. 시크릿을 쓰는 파드는
그중 자기 키만 `/etc/secrets` 파일로 받고 환경변수로는 받지 않는다. 앱 파드는 NetworkPolicy 로 노드 IAM 역할(인스턴스 메타데이터)에
닿지 않고, AWS 를 직접 부르는 `db-backup` 만 예외다.

## DB 백업

CronJob `db-backup` 이 매달 1일 09:00 KST 에 서비스 DB 를 `pg_dump -Fc` 로 떠서 `s3://ggeolmuse-db-backup-apne2/YYYY/MM/` 에 올린다.
1일이 주말이면 클러스터가 꺼져 있어, `startingDeadlineSeconds` 3일로 월요일 기동 때 따라잡는다. RDS 자동 백업(5분 단위 시점 복원)과 달리
RDS 밖 사본이다. 버킷과 쓰기 권한은 `terraform/db-backup.tf` 가 만든다.

## ArgoCD Application

운영 k3s 의 Application 은 모두 리포에 있다.

| Application | 정의 | 동기화 |
|---|---|---|
| `ggeolmuse` | `argocd/application-ggeolmuse.yaml`(앱 차트, `values-prod.yaml`) | 자동 |
| `argocd` | `argocd/application-argocd.yaml`(ArgoCD 자신) | 수동 |
| `k3s-apps` | `argocd/root.yaml`(아래 부품 Application 묶음 `argocd/apps`) | 자동(목록만) |
| `external-secrets` · `prometheus` · `loki` · `tempo` · `k3s-traefik` | `argocd/apps/values.yaml`(차트 버전 고정, 값 파일은 `monitoring/` · `k3s-traefik/`) | 수동 |

부품은 `external-secrets` → `prometheus` → `loki` · `tempo` → `k3s-traefik` 순서로 Sync 한다. 앱 차트의 SecretStore 와 Traefik PodMonitor 가
앞 부품의 CRD 를 쓴다. 관측 구성은 [monitoring/README.md](monitoring/README.md), 멀티 노드 예비안은 [k8s/README.md](../k8s/README.md) 에 있다.
배포 중 메모리 · JVM 힙으로 겪은 일은 [문제 해결](../docs/problem-solving.md) 에 있다.
