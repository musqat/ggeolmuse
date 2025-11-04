#!/bin/bash

# ArgoCD 설치 스크립트

set -e

echo "=========================================="
echo "ArgoCD 설치 시작"
echo "=========================================="

# Helm repo 추가
echo "1. ArgoCD Helm repository 추가..."
helm repo add argo https://argoproj.github.io/argo-helm
helm repo update

# ArgoCD namespace 생성
echo "2. argocd namespace 생성..."
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -

# ArgoCD 설치
echo "3. ArgoCD Helm chart 설치..."
helm upgrade --install argocd argo/argo-cd \
  --namespace argocd \
  --version 5.51.6 \
  --values values-argocd.yaml \
  --wait \
  --timeout 10m

echo ""
echo "=========================================="
echo "ArgoCD 설치 완료!"
echo "=========================================="

# 초기 admin 비밀번호 확인
echo ""
echo "ArgoCD 초기 비밀번호 확인 중..."
ARGOCD_PASSWORD=$(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d)

echo ""
echo "=========================================="
echo "ArgoCD 로그인 정보"
echo "=========================================="
echo "URL: https://argocd.ggeolmuse.com"
echo "Username: admin"
echo "Password: $ARGOCD_PASSWORD"
echo "=========================================="
echo ""
echo "※ UI 또는 'argocd account update-password' 명령어 사용"
echo ""
