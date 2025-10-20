resource "google_compute_instance" "locust_master" {
  name         = "locust-master-vm"
  machine_type = "e2-medium"
  zone         = var.zone
  tags         = ["locust-master", "ssh-iap"]
  allow_stopping_for_update = true

  network_interface {
    subnetwork         = google_compute_subnetwork.perf_private.name
    subnetwork_project = var.project_id
    network_ip         = google_compute_address.locust_master_ip.address
  }

  boot_disk {
    initialize_params {
      image  = "ubuntu-os-cloud/ubuntu-2204-lts"
      size   = 12
      type   = "pd-standard"
    }
  }

  metadata_startup_script = file("${path.module}/scripts/locust_master_setup.sh")

  metadata = {
    enable-oslogin = "TRUE"
    proxy_ip       = google_compute_address.proxy_ip.address
    upstream_ip    = google_compute_address.upstream_ip.address
  }


  labels = {
    role = "locust-master"
    env  = "perf"
  }

  service_account {
    email  = google_service_account.vm_sa.email
    scopes = ["https://www.googleapis.com/auth/cloud-platform"]
  }

  depends_on = [
    google_compute_network.perf_vpc,
    google_compute_subnetwork.perf_private,
    google_compute_address.proxy_ip,
    google_compute_address.upstream_ip
  ]
}
