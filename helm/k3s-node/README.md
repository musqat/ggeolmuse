# k3s 노드 설정

운영 EC2 한 대의 k3s 를 세우는 스크립트와 k3s 가 기동할 때 읽는 설정이다. 차트 밖이라 노드에 직접 둔다. k3s 위 부품 · 앱은 ArgoCD 가 맞춘다.

| 파일 | 하는 일 |
|---|---|
| `bootstrap.sh` | 설정 파일을 두고 버전을 고정한 k3s 를 설치하고, 노드 Ready 뒤 ArgoCD 와 Application(`helm/argocd/root.yaml` · `application-ggeolmuse.yaml`)을 만든다. 관리자 kubeconfig 는 root · ec2-user 그룹만 읽음(0640, ec2-user 는 sudo 없이 kubectl) |
| `config.yaml` | k3s 서버 설정(`/etc/rancher/k3s/config.yaml`). 운영 노드와 같은 값(파드 `10.42.0.0/16` · 서비스 `10.43.0.0/16` 설치 기본값, 번들 Traefik · ServiceLB · local-path 켬, 관리자 kubeconfig `0640` + 그룹 `ec2-user`). 비밀이 없어 644 로 둠(ec2-user 의 kubectl 도 읽음) |
| `kubelet-image-gc.yaml` | 디스크 사용률 70% 에서 안 쓰는 이미지를 지우기 시작해 60% 까지 지운다(kubelet 기본 85 · 80). 릴리스마다 쌓이는 옛 버전 이미지 정리 |
| `bootstrap_test.sh` | `bootstrap.sh --dry-run` 출력 테스트(루트 · 노드 없이) |

목록 값은 `kubelet-arg+:` 로 써서 기존 `config.yaml` 의 `kubelet-arg` 뒤에 더한다. `+` 가 없으면 기존 목록을 덮는다.

## 노드 세우기

새 EC2(Amazon Linux)에 helm 을 깔고 리포를 받은 뒤:

```bash
sudo bash helm/k3s-node/bootstrap.sh --dry-run   # 할 일만 출력
sudo bash helm/k3s-node/bootstrap.sh
```

끝나면 Grafana admin 시크릿(`prometheus-values-prod.yaml` 의 `existingSecret`)을 만든다. 없으면 Grafana 파드가
`CreateContainerConfigError` 로 뜨지 않는다.

```bash
kubectl create namespace monitoring
kubectl -n monitoring create secret generic grafana-admin --from-literal=admin-user=admin \
  --from-literal=admin-password="$(head -c 24 /dev/urandom | base64)"
```

시크릿을 만든 뒤 ArgoCD 화면에서 부품을 [순서대로](../README.md#argocd-gitops) Sync 한다. 앱(`ggeolmuse`)의 자동 동기화가 부품보다 먼저 돌아 실패했으면 부품 Sync 뒤 앱을 한 번 Sync 한다.

새 노드 전용이다. k3s 가 이미 있거나(`/usr/local/bin/k3s` · `k3s.service`) 기존 `/etc/rancher/k3s/config.yaml` 이 리포 파일과 다르면
아무것도 하지 않고 멈춘다. k3s 설치 스크립트가 유닛 인자 · `k3s.service.env` 를 다시 쓰고 k3s 를 재시작해서, 떠 있는 노드에서
돌리면 리포에 없는 설치 옵션이 사라진다.

k3s 버전은 스크립트의 `K3S_VERSION`(지금 `v1.33.5+k3s1`, 운영 노드와 같음)이다.

테스트는 `bash helm/k3s-node/bootstrap_test.sh` 이고 CI 가 shellcheck 와 같이 돌린다.

## 설정 조각만 넣기

이미 떠 있는 노드에는 조각만 옮긴 뒤 EC2 에서:

```bash
sudo mkdir -p /etc/rancher/k3s/config.yaml.d
sudo cp kubelet-image-gc.yaml /etc/rancher/k3s/config.yaml.d/
```

다음 k3s 기동(평일 아침 EC2 시작)부터 반영된다.

## 확인

```bash
kubectl get --raw "/api/v1/nodes/$(kubectl get nodes -o jsonpath='{.items[0].metadata.name}')/proxy/configz" | grep -o '"imageGC[A-Za-z]*":[0-9]*'
```

`imageGCHighThresholdPercent` 70 · `imageGCLowThresholdPercent` 60 이면 들어간 것이다.
