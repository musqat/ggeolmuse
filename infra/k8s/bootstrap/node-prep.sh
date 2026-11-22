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

# 노드 레이블 ggeolmuse.io/node(EC2 이름표의 ggeolmuse-k8s- 뒤, kubelet 이 등록할 때 담)
imds="http://169.254.169.254/latest"
imds_token="$(curl -fsS -X PUT "$imds/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 60")"
node_name="$(curl -fsS -H "X-aws-ec2-metadata-token: $imds_token" "$imds/meta-data/tags/instance/Name" || true)"
if [ -n "$node_name" ]; then
  echo "KUBELET_EXTRA_ARGS=--node-labels=ggeolmuse.io/node=${node_name#*-k8s-}" > /etc/sysconfig/kubelet
fi

# 부품 설치용 helm(받은 파일의 체크섬 확인)
helm_tgz="helm-${HELM_VERSION}-linux-amd64.tar.gz"
curl -fsSLo "/tmp/$helm_tgz" "https://get.helm.sh/$helm_tgz"
echo "$(curl -fsSL "https://get.helm.sh/$helm_tgz.sha256sum" | awk '{print $1}')  /tmp/$helm_tgz" | sha256sum -c -
tar -xzf "/tmp/$helm_tgz" -C /tmp linux-amd64/helm
install -m 755 /tmp/linux-amd64/helm /usr/local/bin/helm
rm -rf "/tmp/$helm_tgz" /tmp/linux-amd64
