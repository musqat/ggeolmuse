# Helm

## 프로젝트에서의 사용

Kubernetes YAML 관리 복잡도(30개 파일, 중복 코드, 환경별 관리 어려움)를 해결하기 위해 도입했다.

**해결한 문제:**

### 1. YAML 중복 제거
```
Before: 6개 서비스 × 5개 YAML = 30개 파일 (중복 많음)
After:  1개 템플릿 + values.yaml로 모든 서비스 관리
```

### 2. 환경별 관리 간소화
```bash
# 개발 환경
helm install ggeolmuse ./ggeolmuse -f values-dev.yaml

# 프로덕션 환경
helm install ggeolmuse ./ggeolmuse -f values-prod.yaml
```

### 3. 템플릿화
```yaml
# templates/deployment.yaml
metadata:
  name: {{ .Values.serviceName }}
spec:
  replicas: {{ .Values.replicaCount }}
  image: {{ .Values.image.repository }}:{{ .Values.image.tag }}
```

### 4. GitOps 통합
- values-prod.yaml 변경 커밋 → ArgoCD 자동 감지 → 배포
- Git으로 배포 이력 관리

---

## 발전 과정

```
Docker Compose (로컬 개발)
  ↓ 프로덕션 부적합
Kubernetes (고가용성 확보)
  ↓ YAML 관리 복잡
Helm (템플릿화 + 환경별 관리) ← 현재
```

---

## 현재 프로젝트 구조

```
helm/
├── ggeolmuse/                # 메인 애플리케이션 차트
│   ├── Chart.yaml
│   ├── values.yaml           # 공통 설정 (base)
│   ├── values-prod.yaml      # 프로덕션 오버라이드
│   └── templates/
│       ├── services/         # User, Trade, MarketData, Backtest
│       ├── infrastructure/   # Redis, Keycloak, Kafka
│       ├── config-server/
│       └── gateway/
│
├── monitoring/
│   ├── prometheus-values.yaml
│   └── custom-dashboards/
│
└── argocd/
    └── application-ggeolmuse.yaml
```

---

## 배포 방법

### 개발 환경
```bash
helm install ggeolmuse ./ggeolmuse \
  -f values-dev.yaml \
  --namespace default
```

### 프로덕션 환경
```bash
helm upgrade --install ggeolmuse ./ggeolmuse \
  --values ./ggeolmuse/values-prod.yaml \
  --namespace default
```

---

## 설정 계층 구조

프로젝트는 4계층 설정 관리를 사용:
1. **Local YML**: 서비스 기본 정보 (name, port, config-server URI)
2. **ConfigServer**: 공통 인프라 + 환경별 설정
3. **Helm**: 배포 설정 (probes, resources)
4. **K8s Secrets**: 민감 정보 외부 주입

---

## 현재 상태

**사용 중:**
- Helm Charts (모든 배포 관리)
- ArgoCD (Git 기반 자동 배포)
- K3s (AWS EC2)

**배포 프로세스:**
```
Git Push (values-prod.yaml 변경)
  ↓
ArgoCD 자동 감지
  ↓
Helm 배포 실행
  ↓
Kubernetes 리소스 업데이트
```

---

## ArgoCD GitOps

values-prod.yaml 변경사항을 Git에 커밋하면 ArgoCD가 자동으로 감지하여 배포한다.

```
argocd/application-ggeolmuse.yaml
  ↓
valueFiles: values-prod.yaml 참조
```
