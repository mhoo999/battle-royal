variable "name" {
  description = "Prefix for every name: battle-royal (production), battle-royal-rehearsal, ..."
  type        = string
}

variable "alarm_prefix" {
  description = "Prefix for alarm names (production's were made as br-...)"
  type        = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_id" {
  description = "Where the server runs; a public subnet (no NAT gateway anywhere)"
  type        = string
}

variable "db_subnet_group" {
  type = string
}

variable "instance_type" {
  type    = string
  default = "t3.micro"
}

variable "instance_profile" {
  description = "From the shared layer: Session Manager, releases, secrets"
  type        = string
}

variable "key_name" {
  description = "Emergency SSH key; port 22 stays closed either way"
  type        = string
  default     = null
}

variable "termination_protection" {
  description = "AWS refuses to terminate the server, console included, while true"
  type        = bool
}

variable "eip_allocation_id" {
  description = "A kept Elastic IP to attach, or null for the subnet's passing public IP"
  type        = string
  default     = null
}

variable "public_ip" {
  description = "The kept IP's address; the bootstrap waits for it before asking for a certificate"
  type        = string
  default     = ""
}

variable "release_bucket" {
  type = string
}

variable "release_key" {
  description = "The jar a new server starts with; CI keeps current/app.jar up to date"
  type        = string
  default     = "current/app.jar"
}

variable "secrets_path" {
  description = "Parameter Store path holding DB_PASSWORD, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET"
  type        = string
}

variable "domain" {
  description = "Public name with a Let's Encrypt certificate, or empty for plain HTTP by IP"
  type        = string
  default     = ""
}

variable "certbot_email" {
  type    = string
  default = ""
}

variable "db_snapshot" {
  description = "Snapshot a new database starts from. Ignored once the database exists."
  type        = string
  default     = null
}

variable "db_deletion_protection" {
  type = bool
}

variable "db_skip_final_snapshot" {
  description = "false keeps a snapshot named <name>-db-final when the database is deleted"
  type        = bool
}

variable "db_backup_retention" {
  description = "Days of automated backups; the Free plan allows 1"
  type        = number
  default     = 1
}

variable "alerts_topic_arn" {
  description = "SNS topic for alarm mail, or null for no alarms"
  type        = string
  default     = null
}

variable "deploy_tag" {
  description = "Value of the Deploy tag that CI targets; production is battle-royal-prod"
  type        = string
}
