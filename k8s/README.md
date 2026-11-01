# 멀티 노드 클러스터

지금 운영은 EC2 한 대의 k3s 다. 이 폴더는 같은 앱을 여러 노드 쿠버네티스로 옮길 때 쓰는 예비안이다.

- 구성은 kubeadm, 컨트롤 플레인 1 · 워커 2
- 전환하면 단일 노드 k3s 를 이것으로 바꾼다. RDS · 시크릿은 그대로 쓴다
- 전환 전에는 바뀌는 것이 없다. k3s 는 이 폴더와 아래 값 파일을 읽지 않고, terraform 모듈은 `k8s_cluster_enabled` 를 켜기 전에는
  아무것도 만들지 않는다
- 로컬에서는 kind 3노드로 같은 구성을 띄워 본다

<br>

## 파일

이 폴더

| 파일 | 하는 일 |
|---|---|
| `versions.env` | 쿠버네티스 · 부품 버전, 파드 · 서비스 대역 |
| `bootstrap/node-prep.sh` | 노드 공통 준비(커널 설정 · containerd · kubelet · kubeadm) |
| `bootstrap/control-plane.sh` · `kubeadm-config.yaml` | 컨트롤 플레인 만들기, 워커 join 명령 출력 |
| `bootstrap/addons.sh` | 부품 설치(Calico · metrics-server · Traefik, AWS 면 EBS CSI · External Secrets · ArgoCD) |
| `addons/` | Calico · Traefik · gp3 저장 클래스 값 |
| `kind/` | 로컬 3노드 올리기 · 확인 · 지우기 |

다른 폴더

| 파일 | 하는 일 |
|---|---|
| `helm/ggeolmuse/values-k8s.yaml` | 멀티 노드 앱 값 |
| `helm/ggeolmuse/values-kind.yaml` | 로컬 kind 앱 값 |
| `helm/argocd/apps` · `helm/argocd/root-k8s.yaml` | ArgoCD 가 맞추는 Application 목록 |
| `helm/monitoring/*-values-k8s.yaml` | 멀티 노드 모니터링 값 |
| [`terraform/modules/k8s-cluster`](../terraform/modules/k8s-cluster) | EC2 셋 · 보안 그룹 · NLB · 노드 역할 |

<br>

## k3s 와 다른 점

| | 지금(k3s) | 멀티 노드 |
|---|---|---|
| 노드 | EC2 1 | EC2 3(컨트롤 플레인 1 · 워커 2) |
| 컨트롤 플레인 | k3s 한 프로세스 · sqlite | kubeadm · etcd |
| 네트워크 · NetworkPolicy | flannel · kube-router | Calico |
| 진입 | 번들 Traefik, EC2 443, IngressRoute | Traefik 2개, NLB 443, 표준 Ingress |
| 저장소 | 노드 디스크(local-path) | EBS gp3 |
| 앱 레플리카 | 전부 1 | 상태 없는 앱 8개 · Keycloak 2개씩, PDB · 노드 분산 |
| Kafka · Zookeeper · Redis | 1 | 1(볼륨만 EBS) |
| DB · 시크릿 | RDS · External Secrets | 같음 |
| 쿠버네티스 | 1.31 | 1.35 |

- 파드 · 서비스 대역은 k3s 와 같다(`10.42.0.0/16` · `10.43.0.0/16`). Keycloak 의 신뢰 프록시 대역을 그대로 쓴다
- 컨트롤 플레인이 멈춰도 떠 있는 파드는 계속 돈다. 배포 · 변경만 멈추고, etcd 는 스냅샷으로 되살린다

<br>

## AWS 에 올리는 순서

노드 접속은 모두 SSM 이고, 스크립트는 노드의 `/opt/ggeolmuse/k8s/bootstrap/` 에 있다.

1. terraform 에 `k8s_cluster_enabled = true` 와 서브넷 · Cloudflare 접두사 목록 · RDS 보안 그룹을 넣고 apply 한다.
   노드는 뜰 때 공통 준비를 마친다
2. 컨트롤 플레인에서 `sudo bash control-plane.sh` 를 돌린다. 마지막 줄에 워커 join 명령이 나온다
3. 워커 둘에서 그 join 명령을 `sudo` 로 돌린다
4. 컨트롤 플레인에서 `bash addons.sh aws` 를 돌린다. 부품을 깔고 ArgoCD 루트 앱을 올려, 그 뒤 앱은 ArgoCD 가 맞춘다
5. ArgoCD 화면에서 `prometheus` · `loki` · `tempo` 를 Sync 한다
6. 앱이 워커 둘에 나뉘었는지, NLB 주소로 https 가 응답하는지 본다
7. Cloudflare DNS 를 NLB 주소(terraform 출력 `k8s_ingress_dns_name`)로 바꾼다

**되돌리기** — DNS 를 k3s EC2 로 돌린다. 같은 RDS 를 보므로 DB 는 그대로다. 멀티 노드를 지울 때는 `k8s_cluster_enabled = false` 로
apply 한다.

**두 클러스터가 같이 떠 있을 때** — 한쪽 앱만 켠다. 같은 RDS 를 보는 스케줄 작업이 양쪽에서 돈다.

<br>

## 로컬 kind 3노드

필요한 것은 Docker · kind · kubectl · helm(버전은 `versions.env`)이다. 앱 이미지는 Docker Hub 의 운영 태그를 받는다.

`kind/up.sh` 로 올리고, `kind/check.sh` 로 확인하고, `kind/down.sh` 로 지운다. 확인하는 것은 노드 분산 · NetworkPolicy · 게이트웨이 응답 ·
워커 하나를 비웠을 때의 응답이다.

kind 에서만 다른 값(`values-kind.yaml`)

| | kind |
|---|---|
| 시크릿 | `up.sh` 가 무작위 값으로 만들어 파일(`/etc/secrets`)로 넣음 |
| DB | 클러스터 안 Postgres |
| 주소 | `http://ggeolmuse.localhost:18080` |
| 모니터링 · 트레이스 | 끔 |
| 저장소 | 노드 디스크(워커를 비우면 Kafka · Zookeeper 는 그 노드가 돌아올 때까지 안 뜸) |
