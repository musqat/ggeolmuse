#!/usr/bin/env bash
# 노드 공통 준비(Amazon Linux 2023): 커널 설정 · containerd · kubelet · kubeadm
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
set -a; . "$here/../versions.env"; set +a
minor="${KUBERNETES_VERSION%.*}"

cat > /etc/modules-load.d/k8s.conf <<CONF
overlay
br_netfilter
CONF
modprobe overlay
modprobe br_netfilter

cat > /etc/sysctl.d/k8s.conf <<CONF
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
CONF
sysctl --system

swapoff -a

# kubelet 과 같은 systemd cgroup
dnf install -y containerd
mkdir -p /etc/containerd
containerd config default > /etc/containerd/config.toml
sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
grep -q 'SystemdCgroup = true' /etc/containerd/config.toml || { echo "containerd 에 SystemdCgroup 줄 없음" >&2; exit 1; }
systemctl enable --now containerd

cat > /etc/yum.repos.d/kubernetes.repo <<CONF
[kubernetes]
name=Kubernetes
baseurl=https://pkgs.k8s.io/core:/stable:/v${minor}/rpm/
enabled=1
gpgcheck=1
gpgkey=https://pkgs.k8s.io/core:/stable:/v${minor}/rpm/repodata/repomd.xml.key
exclude=kubelet kubeadm kubectl cri-tools kubernetes-cni
CONF
dnf install -y "kubelet-${KUBERNETES_VERSION}" "kubeadm-${KUBERNETES_VERSION}" "kubectl-${KUBERNETES_VERSION}" \
  --disableexcludes=kubernetes
systemctl enable kubelet
