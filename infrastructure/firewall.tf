resource "google_compute_firewall" "allow_ssh_iap" {
  name    = "allow-ssh-iap"
  network = google_compute_network.perf_vpc.id

  direction = "INGRESS"
  priority  = 1000

  source_ranges = ["35.235.240.0/20"]

  target_tags = ["ssh-iap"]

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  description = "Allow IAP SSH to instances tagged ssh-iap"
}

resource "google_compute_firewall" "workers_to_proxy" {
  name    = "allow-workers-to-proxy-8080"
  network = google_compute_network.perf_vpc.id

  direction = "INGRESS"
  priority  = 1000

  source_tags = ["locust-worker"]

  target_tags = ["proxy"]

  allow {
    protocol = "tcp"
    ports    = ["8080"]
  }

  description = "Allow Locust workers to reach proxy on 8080"
}

resource "google_compute_firewall" "proxy_to_upstream" {
  name    = "allow-proxy-to-upstream-3000-3001"
  network = google_compute_network.perf_vpc.id

  direction = "INGRESS"
  priority  = 1000

  source_tags = ["proxy"]

  target_tags = ["upstream"]

  allow {
    protocol = "tcp"
    ports    = ["3000-3001"]
  }

  description = "Allow proxy to reach upstream on 3000-3001"
}

resource "google_compute_firewall" "iap_to_locust_ui" {
  name    = "allow-iap-to-locust-ui-8089"
  network = google_compute_network.perf_vpc.id

  direction = "INGRESS"
  priority  = 1000

  source_ranges = ["35.235.240.0/20"]

  target_tags = ["locust-master"]

  allow {
    protocol = "tcp"
    ports    = ["8089"]
  }

  description = "Allow IAP TCP forwarding to Locust UI on port 8089"
}

resource "google_compute_firewall" "locust_internal" {
  name    = "allow-locust-internal-5557-5558"
  network = google_compute_network.perf_vpc.id

  direction = "INGRESS"
  priority  = 1000

  source_tags = ["locust-worker"]
  target_tags = ["locust-master"]

  allow {
    protocol = "tcp"
    ports    = ["5557-5558"]
  }

  description = "Allow Locust master-worker internal communication on 5557–5558"
}
