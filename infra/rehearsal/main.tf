# A rehearsal of rebuilding production: the same stack as infra/prod, started from a
# snapshot of production's database, reached by IP over plain HTTP, unprotected, and
# torn down whole when the check is done (infra/README.md). It never takes deploys
# and never touches production's address, alarms or certificate.

data "terraform_remote_state" "shared" {
  backend = "local"
  config = {
    path = "../shared/terraform.tfstate"
  }
}

module "stack" {
  source = "../modules/game-stack"

  name         = "battle-royal-rehearsal"
  alarm_prefix = "br-rehearsal"

  vpc_id          = "vpc-0223bc622a3c880e2"
  subnet_id       = "subnet-0ec69fdb7684a8c21"
  db_subnet_group = "default-vpc-0223bc622a3c880e2"

  instance_profile = data.terraform_remote_state.shared.outputs.instance_profile
  release_bucket   = data.terraform_remote_state.shared.outputs.release_bucket
  # The restored database carries production's app user, so production's secrets fit.
  secrets_path = "/battle-royal/prod"

  termination_protection = false
  db_deletion_protection = false
  db_skip_final_snapshot = true
  db_snapshot            = var.snapshot

  alerts_topic_arn = null
  deploy_tag       = "none"
}

variable "snapshot" {
  description = "A manual snapshot of production's database to start from"
  type        = string
}

output "url" {
  value = "http://${module.stack.public_ip}/"
}

output "instance_id" {
  value = module.stack.instance_id
}
