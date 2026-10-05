# The permanent layer: what every environment shares and nothing tears down —
# the release bucket, GitHub's way in (OIDC), the instance role, the alert topic, and
# production's public address, so a rebuilt server comes back at the same IP.

locals {
  account_id     = "495791792486"
  release_bucket = "battle-royal-deploy-${local.account_id}"
  # The deploy role may run commands on this instance only. It lives in infra/prod;
  # written out here because prod depends on this layer, not the other way round.
  # Kept until CI targets the Deploy tag below; then it goes.
  prod_instance_arn = "arn:aws:ec2:ap-northeast-2:${local.account_id}:instance/i-02d65fab4965cb3c4"
  # Secrets live under /battle-royal/<environment>/ in Parameter Store.
  secrets_arn = "arn:aws:ssm:ap-northeast-2:${local.account_id}:parameter/battle-royal/*"
}

# --- Production's address: battleroyale.site points here (DNS at the registrar) ---

resource "aws_eip" "prod" {
  domain = "vpc"

  lifecycle {
    prevent_destroy = true
  }
}

# --- Releases: CI uploads the jar, the instance downloads it --------------------

resource "aws_s3_bucket" "deploy" {
  bucket = local.release_bucket
}

resource "aws_s3_bucket_public_access_block" "deploy" {
  bucket                  = aws_s3_bucket.deploy.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "deploy" {
  bucket = aws_s3_bucket.deploy.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "deploy" {
  bucket = aws_s3_bucket.deploy.id
  rule {
    blocked_encryption_types = ["SSE-C"]
    bucket_key_enabled       = true
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# A release is only needed until the next few have replaced it; rollback uses the
# jar kept on the instance (app.jar.prev), not the bucket.
resource "aws_s3_bucket_lifecycle_configuration" "deploy" {
  bucket = aws_s3_bucket.deploy.id
  rule {
    id     = "expire-releases"
    status = "Enabled"
    filter {
      prefix = "releases/"
    }
    expiration {
      days = 14
    }
  }
}

# --- GitHub Actions: short-lived credentials, no keys in the repository ---------

resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["ab9d0263244dd0326eb67015705a667e79cfe998"]
}

resource "aws_iam_role" "deploy" {
  name = "deploy"
  # Only pushes to main of this repository; GitHub's immutable owner/repo ids.
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRoleWithWebIdentity"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          "token.actions.githubusercontent.com:sub" = "repo:mhoo999@144771457/battle-royal@1395286633:ref:refs/heads/main"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "deploy" {
  name = "deployPolicy"
  role = aws_iam_role.deploy.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # releases/<commit>/ for each deploy; current/ is what a new server starts with.
        Effect = "Allow"
        Action = "s3:PutObject"
        Resource = [
          "${aws_s3_bucket.deploy.arn}/releases/*",
          "${aws_s3_bucket.deploy.arn}/current/*",
        ]
      },
      {
        Effect = "Allow"
        Action = "ssm:SendCommand"
        Resource = [
          local.prod_instance_arn,
          "arn:aws:ssm:ap-northeast-2::document/AWS-RunShellScript",
        ]
      },
      {
        # Whichever server carries Deploy=battle-royal-prod, so a rebuilt one with a new
        # instance id is still reached.
        Effect    = "Allow"
        Action    = "ssm:SendCommand"
        Resource  = "arn:aws:ec2:ap-northeast-2:${local.account_id}:instance/*"
        Condition = { StringEquals = { "ssm:resourceTag/Deploy" = "battle-royal-prod" } }
      },
      {
        Effect   = "Allow"
        Action   = ["ssm:GetCommandInvocation", "ec2:DescribeInstances"]
        Resource = "*"
      },
    ]
  })
}

# --- The game server's own identity: Session Manager and release downloads ------

resource "aws_iam_role" "ec2" {
  name        = "battle-royal-ec2"
  description = "Allows EC2 instances to call AWS services on your behalf."
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ec2_ssm" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "ec2_read_releases" {
  name = "read-releases"
  role = aws_iam_role.ec2.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = "s3:GetObject"
      Resource = [
        "${aws_s3_bucket.deploy.arn}/releases/*",
        "${aws_s3_bucket.deploy.arn}/current/*",
      ]
    }]
  })
}

# Read at every start of the game service (the bootstrap's fetch-env.sh). The values
# are SecureStrings under the account's default aws/ssm key, put there by hand
# (scripts/put-secrets.ps1); Terraform never holds them.
resource "aws_iam_role_policy" "ec2_read_secrets" {
  name = "read-secrets"
  role = aws_iam_role.ec2.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["ssm:GetParametersByPath", "ssm:GetParameters", "ssm:GetParameter"]
      Resource = [local.secrets_arn, "arn:aws:ssm:ap-northeast-2:${local.account_id}:parameter/battle-royal"]
    }]
  })
}

resource "aws_iam_instance_profile" "ec2" {
  name = aws_iam_role.ec2.name
  role = aws_iam_role.ec2.name
}

# --- Alerts: CloudWatch alarms mail the operator ---------------------------------

resource "aws_sns_topic" "alerts" {
  name = "battle-royal-alerts"
}

resource "aws_sns_topic_subscription" "alerts_email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}
