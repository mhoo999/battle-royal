# Production: https://battleroyale.site. One game stack in the default VPC
# (docs/AWS_DEPLOYMENT.md §1), protected on the AWS side: the server cannot be
# terminated and the database cannot be deleted until those flags are turned off.
#
# Rebuilding it from a snapshot, and taking it down: infra/README.md.

data "terraform_remote_state" "shared" {
  backend = "local"
  config = {
    path = "../shared/terraform.tfstate"
  }
}

locals {
  shared = data.terraform_remote_state.shared.outputs
}

module "stack" {
  source = "../modules/game-stack"

  name         = "battle-royal"
  alarm_prefix = "br"

  vpc_id          = "vpc-0223bc622a3c880e2" # the default VPC
  subnet_id       = "subnet-0ec69fdb7684a8c21"
  db_subnet_group = "default-vpc-0223bc622a3c880e2"

  instance_profile = local.shared.instance_profile
  key_name         = "battle-royal" # emergency only; port 22 is closed
  release_bucket   = local.shared.release_bucket
  secrets_path     = "/battle-royal/prod"

  eip_allocation_id = local.shared.prod_eip_allocation_id
  public_ip         = local.shared.prod_public_ip
  domain            = "battleroyale.site"
  certbot_email     = var.certbot_email

  termination_protection = var.protected
  db_deletion_protection = var.protected
  db_skip_final_snapshot = false
  db_snapshot            = var.restore_snapshot

  alerts_topic_arn = local.shared.alerts_topic_arn
  deploy_tag       = "battle-royal-prod"
}

# --- Where the hand-imported resources went (2026-10-05) -------------------------

moved {
  from = aws_security_group.web
  to   = module.stack.aws_security_group.web
}
moved {
  from = aws_security_group.db
  to   = module.stack.aws_security_group.db
}
moved {
  from = aws_instance.server
  to   = module.stack.aws_instance.server
}
moved {
  from = aws_eip_association.server
  to   = module.stack.aws_eip_association.server[0]
}
moved {
  from = aws_db_instance.main
  to   = module.stack.aws_db_instance.main
}
moved {
  from = aws_cloudwatch_metric_alarm.ec2_system_check
  to   = module.stack.aws_cloudwatch_metric_alarm.ec2_system_check[0]
}
moved {
  from = aws_cloudwatch_metric_alarm.ec2_instance_check
  to   = module.stack.aws_cloudwatch_metric_alarm.ec2_instance_check[0]
}
moved {
  from = aws_cloudwatch_metric_alarm.ec2_cpu_credits
  to   = module.stack.aws_cloudwatch_metric_alarm.ec2_cpu_credits[0]
}
moved {
  from = aws_cloudwatch_metric_alarm.rds_storage
  to   = module.stack.aws_cloudwatch_metric_alarm.rds_storage[0]
}

# The address now belongs to infra/shared, so a rebuilt server keeps it. Forgotten
# here, never destroyed.
removed {
  from = aws_eip.server
  lifecycle {
    destroy = false
  }
}
