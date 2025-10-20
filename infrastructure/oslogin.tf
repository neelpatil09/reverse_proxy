resource "google_compute_project_metadata_item" "enable_oslogin" {
  key   = "enable-oslogin"
  value = "TRUE"
}

resource "google_compute_project_metadata_item" "block_project_ssh_keys" {
  key   = "block-project-ssh-keys"
  value = "TRUE"
}