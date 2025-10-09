/*
    * Variables for GCP infrastructure
 */
variable "project_id" {
  description = "GCP project ID"
  type        = string
  default = "reverseproxy-474222"
}

variable "region" {
  description = "GCP region"
  type        = string
  default     = "us-central1"
}

variable "zone" {
  description = "GCP zone"
  type        = string
  default     = "us-central1-a"
}

/*
    * Networking + OS Login
 */
variable "vpc_name" {
  description = "VPC name"
  type        = string
  default     = "perf-vpc"
}

variable "subnet_name" {
  description = "Private subnet name"
  type        = string
  default     = "perf-private"
}

variable "subnet_cidr" {
  description = "CIDR for the private subnet"
  type        = string
  default     = "10.10.0.0/20"
}
