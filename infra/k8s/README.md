# 운영 클러스터

운영은 EC2 세 대의 kubeadm 클러스터다(2026-11-22 단일 노드 k3s 에서 옮김).

- 컨트롤 플레인 1(t3a.medium 온디맨드) · 워커 2(m5a.large · t3.large 유지 요청 스팟, 둘이 같이 회수되지 않게 종류를 섞음, 회수되면 멈췄다가 다시 뜸)
- 진입은 Cloudflare Tunnel 이다. cloudflared 둘이 밖으로만 연결하고 경로 규칙으로 서비스에 바로 넘겨, 노드로 들어오는 포트가 없다
- 앱은 ArgoCD 가 master 의 운영 차트(`infra/k8s/charts/ggeolmuse` 에 `values-prod.yaml` · `values-k8s.yaml`)로 맞춘다
- RDS · Secrets Manager 는 클러스터 밖이다. 시크릿은 노드 역할로 External Secrets 가 읽는다
- 평일 07:00 ~ 19:00 에만 켠다(운영 terraform 의 스케줄러)
- 로컬에서는 kind 3노드로 같은 구성을 띄워 본다

<br>

## 파일

| 파일 | 하는 일 |
|---|---|
| `versions.env` | 쿠버네티스 · 부품 버전, 파드 · 서비스 대역 |
| `bootstrap/node-prep.sh` | 노드 공통 준비(커널 설정 · containerd · kubelet · kubeadm) |
| `bootstrap/control-plane.sh` · `kubeadm-config.yaml` | 컨트롤 플레인 만들기, 워커 join 명령 출력 |
| `bootstrap/addons.sh` | 부품 설치(Calico · metrics-server, kind 면 Envoy Gateway, AWS 면 EBS CSI · External Secrets · ArgoCD) |
| `addons/` | Calico · GatewayClass · gp3 저장 클래스 값 |
| `kind/` | 로컬 3노드 올리기 · 확인 · 지우기 |
| `argocd/apps` · `argocd/root.yaml` | ArgoCD 가 맞추는 Application 목록(앱 · 모니터링 셋 · ArgoCD 자신, kind 는 `values-kind.yaml` 도) |
| `charts/ggeolmuse` | 앱 Helm 차트(운영 `values-prod.yaml` 위에 클러스터 `values-k8s.yaml`, kind 는 `values-kind.yaml` 도) |
| `monitoring/` | Prometheus · Loki · Tempo 값(운영 값 위에 `*-values-k8s.yaml` 덧붙임), 알림 규칙 테스트 · 알림 대응 문서 |
| [`../terraform/cluster`](../terraform/cluster) | EC2 셋 · 보안 그룹 · 노드 역할(`modules/k8s-cluster`), 운영 terraform(`../terraform/ops`)과 상태를 따로 둠 |

<br>

## 구성

| | |
|---|---|
| 컨트롤 플레인 | kubeadm · etcd, 앱은 올리지 않음(taint) |
| 네트워크 · NetworkPolicy | Calico(VXLANCrossSubnet, 같은 서브넷 안은 감싸지 않아 노드의 출발지 검사를 끔) |
| 저장소 | EBS gp3(Kafka · Zookeeper · 모니터링) |
| 앱 레플리카 | 상태 없는 앱 8개 · Keycloak 2개씩, PDB · 노드 분산(같은 버전 파드끼리 셈) |
| Kafka · Zookeeper · Redis | 1 |
| 쿠버네티스 | 1.35 |

- 파드 · 서비스 대역은 `10.42.0.0/16` · `10.43.0.0/16` 이다. Keycloak 의 신뢰 프록시 대역도 이 값이다
- 노드마다 레이블 `ggeolmuse.io/node`(control-plane · worker-a · worker-b)를 단다. 노드 준비가 EC2 이름표를 읽어 kubelet 이 등록할 때 넣는다
- 앱 네임스페이스로 들어오는 요청은 기본 거부다. 외부 요청은 cloudflared(kind 는 `envoy-gateway-system` 의 Envoy 프록시)에서 받고, Keycloak 두 대끼리
  캐시 클러스터 포트(7800 · 57800)를 연다. kind 의 Postgres 는 business 서비스 · Keycloak 에서만 받는다
- 워커 하나가 멈추면 1분 뒤 그 노드의 파드를 남은 워커로 옮긴다. 돌아온 뒤 다시 나누는 일은 저절로 하지 않는다
- 노드 셋이 같이 켜질 때 자바 앱 기동이 겹쳐, 자바 앱 기동 검사는 10분까지 기다린다
- 컨트롤 플레인이 멈춰도 떠 있는 파드는 계속 돈다. 배포 · 변경만 멈추고, etcd 는 스냅샷으로 되살린다

<br>

## 로컬 kind 3노드

kind 3노드(`kind/`)로 같은 구성을 로컬에 띄워 노드 분산 · NetworkPolicy · 게이트웨이 응답 · 워커 하나를 비웠을 때의 응답을 본다.
앱 이미지는 Docker Hub 의 운영 태그를 받는다.

kind 에서만 다른 값(`values-kind.yaml`)

| | kind |
|---|---|
| 진입 | Envoy Gateway(Gateway API) |
| 시크릿 | `up.sh` 가 무작위 값으로 만들어 파일(`/etc/secrets`)로 넣음 |
| DB | 클러스터 안 Postgres |
| 주소 | `http://ggeolmuse.localhost:18080` |
| 모니터링 · 트레이스 | 끔 |
| 저장소 | 노드 디스크(워커를 비우면 Kafka · Zookeeper 는 그 노드가 돌아올 때까지 안 뜸) |
