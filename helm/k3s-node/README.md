# k3s 노드 설정

k3s 가 기동할 때 읽는 설정 조각이다. 차트 밖이라 노드의 `/etc/rancher/k3s/config.yaml.d/` 에 직접 둔다.

| 파일 | 하는 일 |
|---|---|
| `kubelet-image-gc.yaml` | 디스크 사용률 70% 에서 안 쓰는 이미지를 지우기 시작해 60% 까지 지운다(kubelet 기본 85 · 80). 릴리스마다 쌓이는 옛 버전 이미지 정리 |

목록 값은 `kubelet-arg+:` 로 써서 기존 `config.yaml` 의 `kubelet-arg` 뒤에 더한다. `+` 가 없으면 기존 목록을 덮는다.

## 넣기

이 파일을 노드로 옮긴 뒤 EC2 에서:

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
