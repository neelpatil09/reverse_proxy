#!/usr/bin/env bash
set -euxo pipefail

MASTER_IP=$(curl -s -H "Metadata-Flavor: Google" \
  http://metadata.google.internal/computeMetadata/v1/instance/attributes/master_ip)
PROXY_IP=$(curl -s -H "Metadata-Flavor: Google" \
  http://metadata.google.internal/computeMetadata/v1/instance/attributes/proxy_ip)
UPSTREAM_IP=$(curl -s -H "Metadata-Flavor: Google" \
  http://metadata.google.internal/computeMetadata/v1/instance/attributes/upstream_ip)

cat >/etc/sysctl.d/99-locust.conf <<'EOF'
net.core.somaxconn = 65535
net.ipv4.ip_local_port_range = 1024 65535
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_fin_timeout = 10
net.core.rmem_max = 67108864
net.core.wmem_max = 67108864
net.ipv4.tcp_rmem = 4096 87380 67108864
net.ipv4.tcp_wmem = 4096 65536 67108864
net.ipv4.tcp_congestion_control = bbr
EOF
sysctl --system

echo "* soft nofile 1048576" >> /etc/security/limits.conf
echo "* hard nofile 1048576" >> /etc/security/limits.conf

apt-get update -y
apt-get install -y python3 python3-pip python3-venv curl
python3 -m venv /opt/locust-venv
/opt/locust-venv/bin/pip install --upgrade pip setuptools wheel
/opt/locust-venv/bin/pip install 'locust[fasthttp]==2.31.1'
ln -sf /opt/locust-venv/bin/locust /usr/local/bin/locust

mkdir -p /home/ubuntu/locust
cat >/home/ubuntu/locust/locustfile.py <<EOF
import os
from locust import FastHttpUser, task, constant

PROXY_URL = os.getenv("PROXY_URL", "http://${PROXY_IP}:8080")
UPSTREAM_HOST_A = os.getenv("UPSTREAM_HOST", "${UPSTREAM_IP}:3000")
UPSTREAM_HOST_B = os.getenv("UPSTREAM_HOST", "${UPSTREAM_IP}:3001")

class ProxyLoadTest(FastHttpUser):
    host = PROXY_URL
    wait_time = constant(0)
    connection_timeout = 3.0
    network_timeout = 10.0
    max_retries = 1
    pool_maxsize = int(os.getenv("POOL_MAXSIZE", "5000"))

    @task(5)
    def hit_proxy_a(self):
        self.client.get("/", headers={"Host": UPSTREAM_HOST_A}, name="/base_a")

    @task(5)
    def hit_proxy_b(self):
      self.client.get("/", headers={"Host": UPSTREAM_HOST_B}, name="/base_b")


#    @task(3)
#    def hit_proxy_large(self):
#        self.client.get("/large", headers={"Host": UPSTREAM_HOST}, name="/large")
EOF

chown -R ubuntu:ubuntu /home/ubuntu/locust
cd /home/ubuntu/locust

ulimit -n 1048576
PROCS=${PROCS:-$(nproc)}

for i in $(seq 1 "$PROCS"); do
  nohup locust -f locustfile.py --worker --master-host "$MASTER_IP" \
    > /var/log/locust-worker-$i.log 2>&1 &
done

echo "Started $PROCS workers registered to master $MASTER_IP"
