#!/usr/bin/env bash
set -euxo pipefail

PROXY_IP=$(curl -s -H "Metadata-Flavor: Google" \
  http://metadata.google.internal/computeMetadata/v1/instance/attributes/proxy_ip)
UPSTREAM_IP=$(curl -s -H "Metadata-Flavor: Google" \
  http://metadata.google.internal/computeMetadata/v1/instance/attributes/upstream_ip)

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
        self.client.get("/", headers={"Host": UPSTREAM_HOST}, name="/base_a")

    @task(5)
    def hit_proxy_b(self):
      self.client.get("/", headers={"Host": UPSTREAM_HOST}, name="/base_b")


#    @task(3)
#    def hit_proxy_large(self):
#        self.client.get("/large", headers={"Host": UPSTREAM_HOST}, name="/large")
EOF

chown -R ubuntu:ubuntu /home/ubuntu/locust
cd /home/ubuntu/locust

nohup locust -f locustfile.py \
  --master \
  --web-host 0.0.0.0 --web-port 8089 \
  --master-bind-host 0.0.0.0 --master-bind-port 5557 \
  > /var/log/locust-master.log 2>&1 &

echo "Locust master started on port 8089"
