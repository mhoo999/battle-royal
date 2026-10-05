terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Local state for now; it moves to an S3 backend once the state bucket exists
  # (docs/ROADMAP.md S1). The state file is git-ignored.
}

provider "aws" {
  region  = "ap-northeast-2"
  profile = "battle-royal"

  # The game's account only. The machine's default profile is a different account;
  # this refuses to touch it even if the profile is misconfigured.
  allowed_account_ids = ["495791792486"]

  # No default_tags yet: they would change every imported resource, and the import is
  # done when `terraform plan` shows no changes. Tags come after, as their own change.
}
