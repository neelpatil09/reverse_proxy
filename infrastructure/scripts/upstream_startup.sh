#!/usr/bin/env bash
set -euxo pipefail

REPO_URL="us-central1-docker.pkg.dev"
REPO_NAME="reverseproxy"
PROJECT_ID="reverseproxy-474222"
IMAGE="${REPO_URL}/${PROJECT_ID}/${REPO_NAME}/upstream:latest"

cat <<EOF | sudo tee /etc/sysctl.d/99-upstream.conf
net.core.somaxconn = 65535
net.ipv4.ip_local_port_range = 1024 65535
fs.file-max = 2097152
EOF
sudo sysctl --system

cat <<EOF | sudo tee -a /etc/security/limits.conf
* soft nofile 1048576
* hard nofile 1048576
EOF

sudo apt-get update -y
sudo apt-get install -y ca-certificates curl gnupg lsb-release docker.io
sudo systemctl enable docker
sudo systemctl start docker

gcloud auth configure-docker "${REPO_URL}" -q
sudo docker pull "${IMAGE}"

sudo docker rm -f upstream || true

sudo docker run -d --name upstream --network host --ulimit nofile=1048576:1048576 "${IMAGE}"

echo "Upstream container launched successfully (host networking, ports 3000/3001)."
