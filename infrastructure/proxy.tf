resource "google_compute_instance" "proxy" {
  name         = "proxy-vm"
  machine_type = "c3-highcpu-8"
  zone         = var.zone
  tags         = ["proxy", "ssh-iap"]
  allow_stopping_for_update = true

  network_interface {
    subnetwork         = google_compute_subnetwork.perf_private.name
    subnetwork_project = var.project_id
    network_ip          = google_compute_address.proxy_ip.address
  }

  boot_disk {
    initialize_params {
      image  = "ubuntu-os-cloud/ubuntu-2204-lts"
      size   = 100
      type   = "pd-ssd"
    }
  }

  # advanced_machine_features {
  #   threads_per_core = 1
  #   visible_core_count = 4
  # }

  metadata_startup_script = file("${path.module}/scripts/proxy_startup.sh")

  labels = {
    role  = "proxy"
    env   = "perf"
    tuned = "true"
  }

  metadata = {
    enable-oslogin   = "TRUE"
    startup-status   = "tuned-highcpu"
  }

  shielded_instance_config {
    enable_secure_boot          = true
    enable_vtpm                 = true
    enable_integrity_monitoring = true
  }

  service_account {
    email  = google_service_account.vm_sa.email
    scopes = [
      "https://www.googleapis.com/auth/cloud-platform",
      "https://www.googleapis.com/auth/monitoring.write"
    ]
  }

  depends_on = [
    google_compute_network.perf_vpc,
    google_compute_subnetwork.perf_private,
    google_compute_address.proxy_ip
  ]
}
