# Kubernetes (k8s)

## 프로젝트에서의 사용

Docker Compose의 한계(단일 호스트, 자동 복구 없음)를 해결하기 위해 프로덕션 배포에 도입했다.

**달성한 목표:**
- 멀티 노드 클러스터 (AWS EC2 K3s)
- Pod 장애 시 자동 재시작
- 무중단 배포 (Rolling Update)
- 서비스 디스커버리 & 로드밸런싱

**새로운 문제:**
```bash
# 서비스 하나당 여러 YAML 파일 필요
user-service/
├── deployment.yaml        # Pod 정의
├── service.yaml           # 네트워크
├── configmap.yaml         # 설정
├── secret.yaml            # 민감 정보
└── servicemonitor.yaml    # 모니터링
```

6개 마이크로서비스 × 5개 YAML = **30개 파일 관리 복잡도**

환경별(dev/prod) 설정 차이를 YAML 중복으로 관리하거나 kustomize 사용 필요 → 복잡함 증가

이 문제를 해결하기 위해 **Helm**을 도입했다.

---

## 현재 상태

이 폴더는 **Helm 차트 개발 시 참고한 원본 매니페스트 백업**

실제 배포는 `../helm/ggeolmuse/` Helm 차트 사용:

```
k8s/ (참고용)                 helm/ggeolmuse/ (실제 사용)
├── kafka.yaml            →   templates/infrastructure/kafka.yaml
├── keycloak.yaml         →   templates/infrastructure/keycloak.yaml
├── user-service.yaml     →   templates/services/user-service/
└── ...                       └── ...
```

**배포 방법:**
```bash
kubectl apply -f k8s/

# Helm 사용
helm install ggeolmuse ./helm/ggeolmuse -f values-dev.yaml
```

---

## Docker Compose vs Kubernetes 비교

| 항목 | Docker Compose | Kubernetes |
|------|----------------|------------|
| **호스트** | 단일 서버 | 멀티 노드 클러스터 |
| **자동 복구** | 없음 | Pod 자동 재시작 |
| **스케일링** | 수동 | HPA 자동 스케일링 |
| **로드밸런싱** | 수동 설정 | Service 자동 분산 |
| **설정 관리** | 환경변수 | ConfigMap/Secret |
| **배포 관리** | 간단 | YAML 30개 관리 복잡 |

---

## 다음 단계

Kubernetes YAML 관리 복잡도를 해결하기 위해 **Helm** 도입
→ 자세한 내용: `../helm/README.md`
