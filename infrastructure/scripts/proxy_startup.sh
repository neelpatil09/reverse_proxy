#!/usr/bin/env bash
set -euxo pipefail

REPO_URL="us-central1-docker.pkg.dev"
REPO_NAME="reverseproxy"
PROJECT_ID="reverseproxy-474222"
IMAGE="${REPO_URL}/${PROJECT_ID}/${REPO_NAME}/proxy:latest"

cat <<EOF | sudo tee /etc/sysctl.d/99-proxy.conf
net.core.somaxconn = 65535
net.ipv4.ip_local_port_range = 1024 65535
fs.file-max = 2097152

net.core.rmem_max = 67108864
net.core.wmem_max = 67108864
net.ipv4.tcp_rmem = 4096 87380 67108864
net.ipv4.tcp_wmem = 4096 65536 67108864
net.ipv4.tcp_congestion_control = bbr
EOF

sudo sysctl --system

cat <<EOF | sudo tee -a /etc/security/limits.conf
* soft nofile 1048576
* hard nofile 1048576
EOF

if command -v ethtool &>/dev/null; then
  sudo ethtool -L eth0 combined 8 || true
  for f in /sys/class/net/eth0/queues/rx-*; do
    echo ffffffff | sudo tee "$f/rps_cpus" || true
  done
fi

sudo apt-get update -y
sudo apt-get install -y ca-certificates curl gnupg lsb-release docker.io ethtool

sudo systemctl enable docker
sudo systemctl start docker

gcloud auth configure-docker ${REPO_URL} -q

sudo docker pull "${IMAGE}"

sudo docker rm -f proxy || true

sudo docker run -d \
  --name proxy \
  --network host \
  --ulimit nofile=1048576:1048576 \
  "${IMAGE}"

echo "Proxy container launched successfully."
