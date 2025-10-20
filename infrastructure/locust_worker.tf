resource "google_compute_instance" "locust_worker" {
  count        = var.locust_worker_count
  name         = "locust-worker-${count.index}"
  machine_type = "c3-highcpu-8"
  zone         = var.zone
  tags         = ["locust-worker", "ssh-iap"]
  allow_stopping_for_update = true

  network_interface {
    subnetwork         = google_compute_subnetwork.perf_private.name
    subnetwork_project = var.project_id
  }

  boot_disk {
    initialize_params {
      image  = "ubuntu-os-cloud/ubuntu-2204-lts"
      size   = 11
      type   = "pd-ssd"
    }
  }

  metadata_startup_script = file("${path.module}/scripts/locust_worker_setup.sh")

  metadata = {
    enable-oslogin = "TRUE"
    master_ip      = google_compute_address.locust_master_ip.address
    proxy_ip       = google_compute_address.proxy_ip.address
    upstream_ip    = google_compute_address.upstream_ip.address
    worker_procs   = "8"               # one proc per vCPU (override here if you want)
  }

  labels = {
    role = "locust-worker"
    env  = "perf"
  }

  service_account {
    email  = google_service_account.vm_sa.email
    scopes = ["https://www.googleapis.com/auth/cloud-platform"]
  }

  depends_on = [
    google_compute_network.perf_vpc,
    google_compute_subnetwork.perf_private,
    google_compute_address.locust_master_ip
  ]
}
