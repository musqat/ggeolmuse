#!/usr/bin/env bash
# 클러스터 부품 설치(버전은 versions.env, 다시 돌려도 같은 결과, 사용: addons.sh aws|kind)
set -euo pipefail

profile="${1:-}"
case "$profile" in
  aws|kind) ;;
  *) echo "사용: $0 aws|kind" >&2; exit 2 ;;
esac

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
set -a; . "$here/../versions.env"; set +a

# 이름이 생길 때까지 대기(사용: wait_for <초> <kubectl get 인자...>)
wait_for() {
  local limit="$1"; shift
  local waited=0
  until kubectl get "$@" >/dev/null 2>&1; do
    [ "$waited" -ge "$limit" ] && { echo "시간 초과: $*" >&2; return 1; }
    sleep 5; waited=$((waited + 5))
  done
}

helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server/ --force-update
helm repo add traefik https://traefik.github.io/charts --force-update
if [ "$profile" = aws ]; then
  helm repo add aws-ebs-csi-driver https://kubernetes-sigs.github.io/aws-ebs-csi-driver --force-update
  helm repo add external-secrets https://charts.external-secrets.io --force-update
  helm repo add argo https://argoproj.github.io/argo-helm --force-update
fi
helm repo update

echo "Calico ${CALICO_VERSION}"
calico="https://raw.githubusercontent.com/projectcalico/calico/${CALICO_VERSION}/manifests"
# Calico API CRD 파일 이름이 Kubernetes 1.36 부터 다름
minor="${KUBERNETES_VERSION#*.}"; minor="${minor%%.*}"
if [ "$minor" -ge 36 ]; then crds=v3_projectcalico_org.yaml; else crds=v3_projectcalico_org-v1beta1.yaml; fi
kubectl apply --server-side --force-conflicts -f "$calico/$crds"
kubectl apply --server-side --force-conflicts -f "$calico/tigera-operator.yaml"
wait_for 300 crd installations.operator.tigera.io
kubectl apply -f "$root/k8s/addons/calico.yaml"
wait_for 600 tigerastatus calico
kubectl wait --for=condition=Available tigerastatus/calico --timeout=10m
kubectl wait --for=condition=Ready nodes --all --timeout=10m

echo "metrics-server ${METRICS_SERVER_CHART}"
# kubelet 서빙 인증서가 자체 서명
helm upgrade --install metrics-server metrics-server/metrics-server --version "$METRICS_SERVER_CHART" \
  -n kube-system --set 'args={--kubelet-insecure-tls}' --wait

echo "Traefik ${TRAEFIK_CHART}"
helm upgrade --install traefik traefik/traefik --version "$TRAEFIK_CHART" \
  -n kube-system -f "$root/k8s/addons/traefik-values.yaml" --wait

[ "$profile" = kind ] && exit 0

echo "EBS CSI ${AWS_EBS_CSI_CHART}"
helm upgrade --install aws-ebs-csi-driver aws-ebs-csi-driver/aws-ebs-csi-driver --version "$AWS_EBS_CSI_CHART" \
  -n kube-system --wait
kubectl apply -f "$root/k8s/addons/storageclass-gp3.yaml"

echo "External Secrets ${EXTERNAL_SECRETS_CHART}"
helm upgrade --install external-secrets external-secrets/external-secrets --version "$EXTERNAL_SECRETS_CHART" \
  -n external-secrets --create-namespace --wait

echo "ArgoCD ${ARGOCD_CHART}"
helm upgrade --install argocd argo/argo-cd --version "$ARGOCD_CHART" \
  -n argocd --create-namespace -f "$root/helm/argocd/values-argocd.yaml" --wait
