# 멀티 노드 클러스터

지금 운영은 EC2 한 대의 k3s 다. 이 폴더는 같은 앱을 여러 노드 쿠버네티스로 옮길 때 쓰는 예비안이다.

- 구성은 kubeadm, 컨트롤 플레인 1(t3a.medium 온디맨드) · 워커 2(m5a.large · t3.large 유지 요청 스팟, 둘이 같이 회수되지 않게 종류를 섞음, 회수되면 멈췄다가 다시 뜸)
- 전환하면 단일 노드 k3s 를 이것으로 바꾼다. RDS · 시크릿은 그대로 쓴다
- 전환 전에는 바뀌는 것이 없다. k3s 는 이 폴더를 읽지 않고, terraform 은 `k8s/terraform` 루트를 apply 하기 전에는 아무것도 만들지 않는다
- 로컬에서는 kind 3노드로 같은 구성을 띄워 본다

<br>

## 파일

이 폴더

| 파일 | 하는 일 |
|---|---|
| `versions.env` | 쿠버네티스 · 부품 버전, 파드 · 서비스 대역 |
| `bootstrap/node-prep.sh` | 노드 공통 준비(커널 설정 · containerd · kubelet · kubeadm) |
| `bootstrap/control-plane.sh` · `kubeadm-config.yaml` | 컨트롤 플레인 만들기, 워커 join 명령 출력 |
| `bootstrap/addons.sh` | 부품 설치(Calico · metrics-server, kind 면 Envoy Gateway, AWS 면 EBS CSI · External Secrets · ArgoCD) |
| `addons/` | Calico · GatewayClass · gp3 저장 클래스 값 |
| `kind/` | 로컬 3노드 올리기 · 확인 · 지우기 |
| `argocd/apps` · `argocd/root.yaml` | ArgoCD 가 맞추는 Application 목록(앱은 운영 차트 `helm/ggeolmuse` 에 `values-k8s.yaml` 을 덧붙임, kind 는 `values-kind.yaml` 도) |
| `monitoring/*-values-k8s.yaml` | 멀티 노드 모니터링 값(운영 값 파일 위에 덧붙임) |
| `terraform/` | EC2 셋 · 보안 그룹 · 노드 역할(`modules/k8s-cluster`), 운영 terraform 과 상태를 따로 둠 |

<br>

## k3s 와 다른 점

| | 지금(k3s) | 멀티 노드 |
|---|---|---|
| 노드 | EC2 1 | EC2 3(컨트롤 플레인 medium 온디맨드 · 워커 large 스팟 둘) |
| 컨트롤 플레인 | k3s 한 프로세스 · sqlite | kubeadm · etcd |
| 네트워크 · NetworkPolicy | flannel · kube-router | Calico |
| 진입 | 번들 Traefik, EC2 443, IngressRoute | Cloudflare Tunnel — cloudflared 2개가 밖으로만 연결하고 경로 규칙으로 서비스에 바로 넘김. 노드로 들어오는 포트는 없음(kind 는 Envoy Gateway) |
| 저장소 | 노드 디스크(local-path) | EBS gp3 |
| 앱 레플리카 | 전부 1 | 상태 없는 앱 8개 · Keycloak 2개씩, PDB · 노드 분산 |
| Kafka · Zookeeper · Redis | 1 | 1(볼륨만 EBS) |
| DB · 시크릿 | RDS · External Secrets | 같음 |
| 쿠버네티스 | 1.31 | 1.35 |

- 파드 · 서비스 대역은 k3s 와 같다(`10.42.0.0/16` · `10.43.0.0/16`). Keycloak 의 신뢰 프록시 대역을 그대로 쓴다
- 앱 네임스페이스로 들어오는 요청은 똑같이 기본 거부다. 외부 요청은 cloudflared(kind 는 `envoy-gateway-system` 의 Envoy 프록시)에서 받고, Keycloak 두 대끼리
  캐시 클러스터 포트(7800 · 57800)를 연다. kind 의 Postgres 는 business 서비스 · Keycloak 에서만 받는다
- 컨트롤 플레인이 멈춰도 떠 있는 파드는 계속 돈다. 배포 · 변경만 멈추고, etcd 는 스냅샷으로 되살린다

<br>

## 로컬 kind 3노드

kind 3노드(`kind/`)로 같은 구성을 로컬에 띄워 노드 분산 · NetworkPolicy · 게이트웨이 응답 · 워커 하나를 비웠을 때의 응답을 본다.
앱 이미지는 Docker Hub 의 운영 태그를 받는다.

kind 에서만 다른 값(`values-kind.yaml`)

| | kind |
|---|---|
| 시크릿 | `up.sh` 가 무작위 값으로 만들어 파일(`/etc/secrets`)로 넣음 |
| DB | 클러스터 안 Postgres |
| 주소 | `http://ggeolmuse.localhost:18080` |
| 모니터링 · 트레이스 | 끔 |
| 저장소 | 노드 디스크(워커를 비우면 Kafka · Zookeeper 는 그 노드가 돌아올 때까지 안 뜸) |
