resource "google_service_account" "vm_sa" {
  account_id   = "perf-vm-sa"
  display_name = "Perf VM Service Account"
}

resource "google_project_iam_member" "sa_logging" {
  role   = "roles/logging.logWriter"
  member = "serviceAccount:${google_service_account.vm_sa.email}"
    project = var.project_id
}

resource "google_project_iam_member" "sa_monitoring" {
  role   = "roles/monitoring.metricWriter"
  member = "serviceAccount:${google_service_account.vm_sa.email}"
  project = var.project_id
}

resource "google_project_iam_member" "sa_registry_reader" {
  role   = "roles/artifactregistry.reader"
  member = "serviceAccount:${google_service_account.vm_sa.email}"
  project = var.project_id
}
