# ========================================
# Variables
# ========================================
variable "application_name" {
  type    = string
  default = "aggregator"
}

variable "aws_region" {
  type    = string
  default = "eu-central-1"
}

variable "local_output" {
  type    = bool
  default = false
}

variable "application_version" {
  type = string
}
