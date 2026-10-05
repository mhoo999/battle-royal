# infra — Terraform

The AWS resources behind https://battleroyale.site, as code. They were built in the
console first (`docs/AWS_DEPLOYMENT.md` §4) and imported on 2026-10-05; `imports.tf`
in each layer records where each one came from.

| Layer | What | Torn down? |
|---|---|---|
| `shared/` | release bucket, GitHub OIDC provider and `deploy` role, the instance role and profile, the alert topic | never |
| `prod/` | the two security groups, the EC2 instance and its Elastic IP, RDS MySQL, four alarms | never; `prevent_destroy` on the server, the IP and the database |

A scale-out environment for experiments (ALB, two servers, Redis) will be its own layer,
brought up and down whole (`docs/ROADMAP.md` S3).

## Use

```sh
aws login --profile battle-royal --region ap-northeast-2   # the game's account
cd infra/shared   # first; prod reads its outputs
terraform init
terraform plan    # expect "No changes"
```

- The provider is pinned to account `495791792486` (`allowed_account_ids`): this machine's
  default profile belongs to another account, and Terraform refuses it.
- `shared/terraform.tfvars` holds the alert e-mail and is git-ignored; copy
  `terraform.tfvars.example`.
- State is local and git-ignored for now. Moving it to an S3 backend is the next step
  (it needs one new bucket).
- `apply` is run by the operator, not by CI. Pushes that only touch `infra/` do not
  redeploy the game.

## Not managed here

- The RDS master password (set in the console) and `/etc/battle-royal/env` on the server.
- The key pair `battle-royal` (referenced by name; port 22 is closed).
- DNS (at the registrar) and the Let's Encrypt certificate (certbot on the instance).
- The default VPC, its subnets and the default DB subnet group.
