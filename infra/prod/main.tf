# The production layer: one game server and its database, in the default VPC
# (docs/AWS_DEPLOYMENT.md §1). Never torn down; prevent_destroy guards what holds
# players' data or the public address.

data "terraform_remote_state" "shared" {
  backend = "local"
  config = {
    path = "../shared/terraform.tfstate"
  }
}

locals {
  shared = data.terraform_remote_state.shared.outputs
  vpc_id = "vpc-0223bc622a3c880e2" # the default VPC
}

# --- Network: the internet reaches Nginx only, the database reaches nothing ---

resource "aws_security_group" "web" {
  name        = "battle-royal-web"
  description = "battle-royal web server"
  vpc_id      = local.vpc_id

  # No SSH: the server is reached through Session Manager.
  ingress {
    description = "game"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description = "game https"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "db" {
  name        = "battle-royal-db"
  description = "battle-royal rds"
  vpc_id      = local.vpc_id

  ingress {
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.web.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# --- The game server: jar + systemd behind Nginx --------------------------------

resource "aws_instance" "server" {
  ami                    = "ami-0870825cefcaafcc8" # Amazon Linux 2023, as launched
  instance_type          = "t3.micro"
  subnet_id              = "subnet-0ec69fdb7684a8c21"
  vpc_security_group_ids = [aws_security_group.web.id]
  iam_instance_profile   = local.shared.instance_profile
  key_name               = "battle-royal" # emergency only; port 22 is closed

  # Bursts past the CPU credits are billed rather than throttled.
  credit_specification {
    cpu_credits = "unlimited"
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 10
    iops        = 3000
    throughput  = 125
  }

  tags = {
    Name = "battle-royal-server"
  }

  lifecycle {
    prevent_destroy = true
    # A newer AMI must never replace the running server by surprise.
    ignore_changes = [ami]
  }
}

resource "aws_eip" "server" {
  domain = "vpc"

  # battleroyale.site points here (DNS at the registrar).
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_eip_association" "server" {
  allocation_id = aws_eip.server.id
  instance_id   = aws_instance.server.id
}

# --- The database: accounts, stash, sorties, seasons ----------------------------

resource "aws_db_instance" "main" {
  identifier     = "battle-royal-db"
  engine         = "mysql"
  engine_version = "8.4.11"
  instance_class = "db.t4g.micro"
  db_name        = "battleroyal"
  username       = "admin"
  # The master password was set in the console and is not managed here.

  allocated_storage = 20
  storage_type      = "gp2"
  storage_encrypted = true

  db_subnet_group_name   = "default-vpc-0223bc622a3c880e2"
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  parameter_group_name   = "default.mysql8.4"
  option_group_name      = "default:mysql-8-4"

  # A week is wanted (docs/ROADMAP.md C1), but the account's Free plan refuses more
  # than 1 day (FreeTierRestrictionError, 2026-10-05). Raise it to 7 with the move to a
  # paid plan (C15); until then manual snapshots cover the season wipe (C2).
  backup_retention_period = 1
  backup_window           = "16:05-16:35"
  maintenance_window      = "wed:13:02-wed:13:32"
  copy_tags_to_snapshot   = true

  # 8.4 is on standard support; never fall into paid Extended Support.
  engine_lifecycle_support = "open-source-rds-extended-support-disabled"

  # Deleting the database takes two deliberate steps, in the console as well.
  deletion_protection = true

  # If this is ever deleted anyway, leave a snapshot behind.
  skip_final_snapshot       = false
  final_snapshot_identifier = "battle-royal-db-final"

  lifecycle {
    prevent_destroy = true
  }
}

# --- Alarms: mail the operator; let AWS recover or reboot a sick instance -------

resource "aws_cloudwatch_metric_alarm" "ec2_system_check" {
  alarm_name          = "br-ec2-system-check"
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed_System"
  dimensions          = { InstanceId = aws_instance.server.id }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = 1
  alarm_actions = [
    "arn:aws:automate:ap-northeast-2:ec2:recover",
    local.shared.alerts_topic_arn,
  ]
}

resource "aws_cloudwatch_metric_alarm" "ec2_instance_check" {
  alarm_name          = "br-ec2-instance-check"
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed_Instance"
  dimensions          = { InstanceId = aws_instance.server.id }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = 1
  alarm_actions = [
    local.shared.alerts_topic_arn,
    "arn:aws:swf:ap-northeast-2:495791792486:action/actions/AWS_EC2.InstanceId.Reboot/1.0",
  ]
}

resource "aws_cloudwatch_metric_alarm" "ec2_cpu_credits" {
  alarm_name          = "br-ec2-cpu-credits"
  namespace           = "AWS/EC2"
  metric_name         = "CPUCreditBalance"
  dimensions          = { InstanceId = aws_instance.server.id }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  comparison_operator = "LessThanThreshold"
  threshold           = 20
  alarm_actions       = [local.shared.alerts_topic_arn]
}

resource "aws_cloudwatch_metric_alarm" "rds_storage" {
  alarm_name          = "br-rds-storage"
  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.main.identifier }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  comparison_operator = "LessThanThreshold"
  threshold           = 2147483648 # 2 GiB
  alarm_actions       = [local.shared.alerts_topic_arn]
}
