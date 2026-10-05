# One game stack: a server (jar + systemd behind Nginx) and its MySQL database, with
# their security groups and alarms. Production is one instance of this module; a
# rehearsal or an experiment is another, brought up and torn down whole.
#
# Protection lives on the AWS side (termination and deletion protection), not in
# prevent_destroy, so it can differ per stack and holds in the console too.

locals {
  db_url = "jdbc:mysql://${aws_db_instance.main.address}:3306/battleroyal?sslMode=REQUIRED&serverTimezone=UTC"
}

data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]
  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }
}

# --- Network: the internet reaches Nginx only, the database reaches nothing ---

resource "aws_security_group" "web" {
  name        = "${var.name}-web"
  description = "${var.name} web server"
  vpc_id      = var.vpc_id

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
  name        = "${var.name}-db"
  description = "${var.name} rds"
  vpc_id      = var.vpc_id

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

# --- The database: accounts, stash, sorties, seasons ----------------------------

resource "aws_db_instance" "main" {
  identifier          = "${var.name}-db"
  snapshot_identifier = var.db_snapshot
  engine              = "mysql"
  engine_version      = "8.4.11"
  instance_class      = "db.t4g.micro"
  db_name             = "battleroyal"
  username            = "admin"
  # The master password comes with the snapshot; it is not managed here.

  allocated_storage = 20
  storage_type      = "gp2"
  storage_encrypted = true

  db_subnet_group_name   = var.db_subnet_group
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  parameter_group_name   = "default.mysql8.4"
  option_group_name      = "default:mysql-8-4"

  backup_retention_period = var.db_backup_retention
  backup_window           = "16:05-16:35"
  maintenance_window      = "wed:13:02-wed:13:32"
  copy_tags_to_snapshot   = true

  # 8.4 is on standard support; never fall into paid Extended Support.
  engine_lifecycle_support = "open-source-rds-extended-support-disabled"

  deletion_protection       = var.db_deletion_protection
  skip_final_snapshot       = var.db_skip_final_snapshot
  final_snapshot_identifier = var.db_skip_final_snapshot ? null : "${var.name}-db-final"

  lifecycle {
    # The snapshot only matters at creation; changing it later must not rebuild a
    # live database.
    ignore_changes = [snapshot_identifier]
  }
}

# --- The game server -------------------------------------------------------------

resource "aws_instance" "server" {
  ami                     = data.aws_ami.al2023.id
  instance_type           = var.instance_type
  subnet_id               = var.subnet_id
  vpc_security_group_ids  = [aws_security_group.web.id]
  iam_instance_profile    = var.instance_profile
  key_name                = var.key_name
  disable_api_termination = var.termination_protection

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

  # Carriage returns stripped: a Windows checkout must not reach bash or Nginx.
  user_data = replace(templatefile("${path.module}/user-data.sh.tftpl", {
    release_bucket = var.release_bucket
    release_key    = var.release_key
    secrets_path   = var.secrets_path
    db_url         = local.db_url
    domain         = var.domain
    certbot_email  = var.certbot_email
    public_ip      = var.public_ip
    service_b64    = base64encode(replace(file("${path.module}/../../../deploy/battle-royal.service"), "\r", ""))
    nginx_b64      = base64encode(replace(file("${path.module}/nginx.conf"), "\r", ""))
    site_b64       = base64encode(replace(templatefile("${path.module}/site.conf.tftpl", { domain = var.domain }), "\r", ""))
  }), "\r", "")

  tags = {
    Name   = "${var.name}-server"
    Deploy = var.deploy_tag
  }

  lifecycle {
    # Servers are rebuilt, not edited: a new AMI or bootstrap applies to the next
    # server, never to the running one (production's was set up by hand).
    ignore_changes = [ami, user_data]
  }
}

resource "aws_eip_association" "server" {
  count         = var.eip_allocation_id == null ? 0 : 1
  allocation_id = var.eip_allocation_id
  instance_id   = aws_instance.server.id
}

# --- Alarms: mail the operator; let AWS recover or reboot a sick instance -------

resource "aws_cloudwatch_metric_alarm" "ec2_system_check" {
  count               = var.alerts_topic_arn == null ? 0 : 1
  alarm_name          = "${var.alarm_prefix}-ec2-system-check"
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
    var.alerts_topic_arn,
  ]
}

resource "aws_cloudwatch_metric_alarm" "ec2_instance_check" {
  count               = var.alerts_topic_arn == null ? 0 : 1
  alarm_name          = "${var.alarm_prefix}-ec2-instance-check"
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
    var.alerts_topic_arn,
    "arn:aws:swf:ap-northeast-2:495791792486:action/actions/AWS_EC2.InstanceId.Reboot/1.0",
  ]
}

resource "aws_cloudwatch_metric_alarm" "ec2_cpu_credits" {
  count               = var.alerts_topic_arn == null ? 0 : 1
  alarm_name          = "${var.alarm_prefix}-ec2-cpu-credits"
  namespace           = "AWS/EC2"
  metric_name         = "CPUCreditBalance"
  dimensions          = { InstanceId = aws_instance.server.id }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  comparison_operator = "LessThanThreshold"
  threshold           = 20
  alarm_actions       = [var.alerts_topic_arn]
}

resource "aws_cloudwatch_metric_alarm" "rds_storage" {
  count               = var.alerts_topic_arn == null ? 0 : 1
  alarm_name          = "${var.alarm_prefix}-rds-storage"
  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.main.identifier }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  comparison_operator = "LessThanThreshold"
  threshold           = 2147483648 # 2 GiB
  alarm_actions       = [var.alerts_topic_arn]
}
