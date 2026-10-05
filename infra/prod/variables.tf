variable "certbot_email" {
  description = "Let's Encrypt account e-mail, used when a rebuilt server asks for a certificate. In terraform.tfvars (git-ignored)."
  type        = string
}

variable "protected" {
  description = "Termination protection on the server, deletion protection on the database. Turned off only to take production down on purpose."
  type        = bool
  default     = true
}

variable "restore_snapshot" {
  description = "Snapshot a rebuilt database starts from. Ignored while the database exists."
  type        = string
  default     = null
}
