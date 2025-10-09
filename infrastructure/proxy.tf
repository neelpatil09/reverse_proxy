resource "google_compute_instance" "proxy" {
  name         = "proxy-vm"
  machine_type = "e2-small" #""e2-highcpu-8"
  zone         = var.zone
  tags         = ["proxy", "ssh-iap"]
  allow_stopping_for_update = true

  network_interface {
    subnetwork         = google_compute_subnetwork.perf_private.name
    subnetwork_project = var.project_id

    network_ip = google_compute_address.proxy_ip.address

    access_config {}
  }

  boot_disk {
    initialize_params {
      image  = "ubuntu-os-cloud/ubuntu-2204-lts"
      size   = 50
      type   = "pd-balanced"
    }
  }

  metadata_startup_script = file("${path.module}/scripts/proxy_startup.sh")

  labels = {
    role = "proxy"
    env  = "perf"
  }

  metadata = {
    enable-oslogin = "TRUE"
  }

  shielded_instance_config {
    enable_secure_boot          = true
    enable_vtpm                 = true
    enable_integrity_monitoring = true
  }

  service_account {
    email  = google_service_account.vm_sa.email
    scopes = ["https://www.googleapis.com/auth/cloud-platform"]
  }

  depends_on = [
    google_compute_network.perf_vpc,
    google_compute_subnetwork.perf_private,
    google_compute_address.proxy_ip
  ]

}
