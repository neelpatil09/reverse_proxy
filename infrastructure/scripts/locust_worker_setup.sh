#!/usr/bin/env bash
set -euxo pipefail

MASTER_IP=$(curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/attributes/master_ip || echo "127.0.0.1")
PROXY_IP=$(curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/attributes/proxy_ip || echo "127.0.0.1")
UPSTREAM_IP=$(curl -s -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/attributes/upstream_ip || echo "127.0.0.1")

sudo tee /etc/sysctl.d/99-locust.conf >/dev/null <<'EOF'
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
sudo sysctl --system

sudo tee -a /etc/security/limits.conf >/dev/null <<'EOF'
* soft nofile 1048576
* hard nofile 1048576
EOF

sudo apt-get update -y
sudo apt-get install -y python3 python3-pip python3-venv curl jq
sudo python3 -m venv /opt/locust-venv
sudo /opt/locust-venv/bin/pip install --upgrade pip setuptools wheel
sudo /opt/locust-venv/bin/pip install 'locust[fasthttp]==2.31.1'

sudo mkdir -p /home/ubuntu/locust
sudo tee /home/ubuntu/locust/locustfile.py >/dev/null <<EOF
import os
from locust import FastHttpUser, task, constant

PROXY_URL = os.getenv("PROXY_URL", "http://${PROXY_IP}:8080")
UPSTREAM_HOST = os.getenv("UPSTREAM_HOST", "${UPSTREAM_IP}:3000")

class ProxyLoadTest(FastHttpUser):
    host = PROXY_URL
    wait_time = constant(0)
    connection_timeout = 1.0
    network_timeout = 1.0
    max_retries = 0
    pool_maxsize = int(os.getenv("POOL_MAXSIZE", "5000"))

    @task
    def hit_proxy(self):
        self.client.get("/", headers={"Host": UPSTREAM_HOST}, name="/to-upstream")
EOF
sudo chown -R ubuntu:ubuntu /home/ubuntu/locust

sudo tee /usr/local/bin/locust-worker >/dev/null <<'EOS'
#!/usr/bin/env bash
set -euo pipefail
exec /opt/locust-venv/bin/locust -f /home/ubuntu/locust/locustfile.py --worker "$@"
EOS
sudo chmod +x /usr/local/bin/locust-worker

sudo tee /home/ubuntu/README.txt >/dev/null <<EOM
Locust worker ready (venv at /opt/locust-venv).

Env (optional):
  export PROXY_URL=http://${PROXY_IP}:8080
  export UPSTREAM_HOST=${UPSTREAM_IP}:3000
  export POOL_MAXSIZE=5000

Before starting:
  ulimit -n 1048576

Join master (${MASTER_IP}):
  locust-worker --master-host ${MASTER_IP}

Multiple workers per VM:
  PROCS=\$(nproc); ulimit -n 1048576
  for i in \$(seq 1 \$PROCS); do nohup locust-worker --master-host ${MASTER_IP} > /var/tmp/locust-worker-\$i.log 2>&1 & done
EOM

echo "Worker tuned & installed. Use 'locust-worker --master-host ${MASTER_IP}'."
