# 인프라 · 배포

## 인프라 구성

```
Cloudflare (DNS · 프록시 · Access · 꺼져 있을 때 안내 Worker)
    │ Cloudflare Tunnel(cloudflared 가 밖으로 연결, 노드로 들어오는 포트 없음)
AWS EC2 셋 — kubeadm 클러스터
    ├── 컨트롤 플레인 t3a.medium 온디맨드(앱 없음)
    ├── 워커 m5a.large · t3.large 스팟
    │     ├── cloudflared · ArgoCD (GitOps)
    │     ├── 관측: Prometheus · Grafana · AlertManager · Loki · Tempo (OpenTelemetry Java Agent)
    │     ├── 인프라 파드: Kafka + Zookeeper · Redis · Keycloak
    │     └── 앱 파드: gateway-server · config-server · user · trade · market-data · backtest · chat · frontend-web
    └── EBS gp3 — Kafka · Zookeeper · 모니터링 볼륨

AWS RDS db.t4g.micro (PostgreSQL 15, Single-AZ) — 서비스 DB 넷 + Keycloak
AWS Secrets Manager — 앱 · Tunnel 자격 시크릿
AWS S3 — 월간 DB 덤프
```

클러스터 구성 · 노드 분산은 [k8s/README.md](../k8s/README.md) 에 있다.

## 구성 등급

아키텍처는 그대로 두고 운영 등급만 낮췄다.

| 구성 | 관리형으로 짜면 | 지금 |
|---|---|---|
| Kubernetes | EKS | kubeadm(EC2 셋, 워커 스팟) |
| Database | RDS Multi-AZ | RDS Single-AZ |
| Cache | ElastiCache | 파드 안 Redis |
| Message Queue | MSK | 파드 안 Kafka |
| Load Balancer | ALB | Cloudflare Tunnel |

포기한 것은 컨트롤 플레인 · DB · Kafka 의 고가용성, 자동 스케일링, 관리형 서비스이고, 남긴 것은 마이크로서비스 구조 · 컨테이너 ·
이벤트 기반 흐름 · 관측 · 워커 하나가 멈춰도 도는 앱 레플리카 둘이다.

## 배포 흐름

```
PR — GitHub Actions: 서비스 빌드 · 테스트 · 커버리지 최소치, 이미지 빌드 → Trivy 스캔, E2E(docker compose)
    ↓ master 머지 뒤 CI · E2E 초록
릴리스 — PR 본문에서 체크한 서비스만 이미지 빌드 → Trivy → Docker Hub(muscathan/*) 푸시,
         릴리스 봇이 values-prod.yaml 의 이미지 태그를 master 에 커밋
    ↓
ArgoCD — master 의 helm/ggeolmuse 를 자동 동기화(지운 리소스는 지우고, 손으로 바꾼 것은 되돌림) → kubeadm 클러스터
```

## 값 파일과 시크릿

| 파일 | 담는 것 |
|---|---|
| `values.yaml` | 공통(이미지 레지스트리 · 포트 · Kafka 토픽 · probe · 자원 기본값) |
| `values-dev.yaml` | 로컬(app.localhost · 표준 Ingress · dev 프로파일) |
| `values-prod.yaml` | 운영(ggeolmuse.com · Secrets Manager · 서비스별 이미지 태그 · 자원) |
| `values-k8s.yaml` | 클러스터(레플리카 둘 · 노드 분산 · PDB · cloudflared · EBS gp3 · 기동 검사 10분), `values-prod.yaml` 위에 덧붙임 |
| `values-kind.yaml` | 로컬 kind 3노드(Envoy Gateway · 클러스터 안 Postgres), `values-k8s.yaml` 위에 덧붙임 |
| `values-secret.yaml` | 로컬 시크릿(git 에 없음, `.example` 만) |

운영 시크릿은 Secrets Manager `ggeolmuse/production` 을 External Secrets 가 `ggeolmuse-secrets` 로 내려준다. 시크릿을 쓰는 파드는
그중 자기 키만 `/etc/secrets` 파일로 받고 환경변수로는 받지 않는다. Tunnel 자격은 `ggeolmuse/cloudflared` 를 `cloudflared-credentials` 로
받는다. 앱 파드는 NetworkPolicy 로 노드 IAM 역할(인스턴스 메타데이터)에 닿지 않고, AWS 를 직접 부르는 `db-backup` 만 예외다.

## DB 백업

CronJob `db-backup` 이 매달 1일 09:00 KST 에 서비스 DB 를 `pg_dump -Fc` 로 떠서 `s3://ggeolmuse-db-backup-apne2/YYYY/MM/` 에 올린다.
1일이 주말이면 클러스터가 꺼져 있어, `startingDeadlineSeconds` 3일로 월요일 기동 때 따라잡는다. RDS 자동 백업(5분 단위 시점 복원)과 달리
RDS 밖 사본이다. 버킷과 쓰기 권한은 `terraform/db-backup.tf` 가 만든다.

## ArgoCD Application

클러스터의 Application 은 `k8s/argocd` 에 있다.

| Application | 정의 | 동기화 |
|---|---|---|
| `ggeolmuse-apps` | `k8s/argocd/root.yaml`(아래 Application 묶음 `k8s/argocd/apps`) | 자동(목록만) |
| `ggeolmuse` | 앱 차트 `helm/ggeolmuse`(`values-prod.yaml` · `values-k8s.yaml`) | 자동 |
| `prometheus` · `loki` · `tempo` | 차트 버전 고정, 값 파일은 `monitoring/` 위에 `k8s/monitoring/` | 수동(prometheus 는 server-side apply) |
| `argocd` | ArgoCD 자신 | 수동 |

앱 차트의 ServiceMonitor · PrometheusRule 이 prometheus 의 CRD 를 쓰므로 prometheus 를 먼저 Sync 한다. 관측 구성은
[monitoring/README.md](monitoring/README.md) 에 있다. 배포 중 메모리 · JVM 힙으로 겪은 일은 [문제 해결](../docs/problem-solving.md) 에 있다.
