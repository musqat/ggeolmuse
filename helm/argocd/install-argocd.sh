#!/bin/bash
# ArgoCD 설치. values-argocd.yaml 기준
set -e

echo "helm repo 추가"
helm repo add argo https://argoproj.github.io/argo-helm
helm repo update

echo "namespace argocd"
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -

echo "argo-cd 9.0.6 설치"
helm upgrade --install argocd argo/argo-cd \
  --namespace argocd \
  --version 9.0.6 \
  --values values-argocd.yaml \
  --wait \
  --timeout 10m

ARGOCD_PASSWORD=$(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d)

echo "설치 완료"
echo "URL       https://argocd.ggeolmuse.com"
echo "Username  admin"
echo "Password  $ARGOCD_PASSWORD"
