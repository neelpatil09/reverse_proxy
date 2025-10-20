#!/usr/bin/env bash
set -euxo pipefail

PROXY_IP=$(curl -s -H "Metadata-Flavor: Google" \
  http://metadata.google.internal/computeMetadata/v1/instance/attributes/proxy_ip)
UPSTREAM_IP=$(curl -s -H "Metadata-Flavor: Google" \
  http://metadata.google.internal/computeMetadata/v1/instance/attributes/upstream_ip)

sudo apt-get update -y
sudo apt-get install -y python3 python3-pip python3-venv git curl

sudo python3 -m venv /opt/locust-venv
sudo /opt/locust-venv/bin/pip install --upgrade pip setuptools wheel
sudo /opt/locust-venv/bin/pip install 'locust[fasthttp]==2.31.1'

sudo -u ubuntu mkdir -p /home/ubuntu/locust
cat <<EOF | sudo -u ubuntu tee /home/ubuntu/locust/locustfile.py > /dev/null
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

sudo tee /usr/local/bin/locust-master >/dev/null <<'EOS'
#!/usr/bin/env bash
set -euo pipefail
exec /opt/locust-venv/bin/locust -f /home/ubuntu/locust/locustfile.py \
  --master --web-host 0.0.0.0 --web-port 8089 \
  --master-bind-host 0.0.0.0 --master-bind-port 5557 \
  "$@"
EOS
sudo chmod +x /usr/local/bin/locust-master

cat <<EOF | sudo -u ubuntu tee /home/ubuntu/README.txt > /dev/null
Locust master setup complete (venv at /opt/locust-venv).

Start the web UI (workers can join anytime):
  export PROXY_URL=http://${PROXY_IP}:8080
  export UPSTREAM_HOST=${UPSTREAM_IP}:3000
  nohup locust-master > /var/log/locust-master.log 2>&1 &

Open UI:
  http://<MASTER_IP>:8089
  (In the UI, set Host to: http://${PROXY_IP}:8080)

Workers connect with:
  /opt/locust-venv/bin/locust -f /home/ubuntu/locust/locustfile.py --worker \\
    --master-host <master_internal_ip>
EOF

echo "Locust master installed via venv. Use 'nohup locust-master > /var/log/locust-master.log 2>&1 &'"
