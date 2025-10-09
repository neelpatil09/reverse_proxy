resource "google_compute_network" "perf_vpc" {
  name                    = var.vpc_name
  auto_create_subnetworks = false
  routing_mode            = "REGIONAL"
  mtu                     = 1460
}

resource "google_compute_subnetwork" "perf_private" {
  name                     = var.subnet_name
  ip_cidr_range            = var.subnet_cidr
  region                   = var.region
  network                  = google_compute_network.perf_vpc.id
  private_ip_google_access = true
}

resource "google_compute_router" "perf_router" {
  name    = "${var.vpc_name}-router"
  region  = var.region
  network = google_compute_network.perf_vpc.id
}

resource "google_compute_router_nat" "perf_nat" {
  name   = "${var.vpc_name}-nat"
  router = google_compute_router.perf_router.name
  region = var.region

  nat_ip_allocate_option               = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat   = "ALL_SUBNETWORKS_ALL_IP_RANGES"

  tcp_established_idle_timeout_sec   = 1200
  tcp_transitory_idle_timeout_sec    = 30
  udp_idle_timeout_sec               = 30
  icmp_idle_timeout_sec              = 30

  log_config {
    enable = true
    filter = "ERRORS_ONLY"
  }
}
