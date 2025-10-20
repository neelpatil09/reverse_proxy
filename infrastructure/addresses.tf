resource "google_compute_address" "proxy_ip" {
  name         = "proxy-ip"
  address_type = "INTERNAL"
  subnetwork   = google_compute_subnetwork.perf_private.id
  region       = var.region
}

resource "google_compute_address" "upstream_ip" {
  name         = "upstream-ip"
  address_type = "INTERNAL"
  subnetwork   = google_compute_subnetwork.perf_private.id
  region       = var.region
}

resource "google_compute_address" "locust_master_ip" {
  name         = "locust-master-ip"
  address_type = "INTERNAL"
  subnetwork   = google_compute_subnetwork.perf_private.id
  region       = var.region
}
