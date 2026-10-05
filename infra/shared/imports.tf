# One-time imports of what was built in the console (docs/AWS_DEPLOYMENT.md §4).
# Kept as a record of where each resource came from; harmless once imported.

import {
  to = aws_s3_bucket.deploy
  id = "battle-royal-deploy-495791792486"
}
import {
  to = aws_s3_bucket_public_access_block.deploy
  id = "battle-royal-deploy-495791792486"
}
import {
  to = aws_s3_bucket_lifecycle_configuration.deploy
  id = "battle-royal-deploy-495791792486"
}
import {
  to = aws_s3_bucket_server_side_encryption_configuration.deploy
  id = "battle-royal-deploy-495791792486"
}
import {
  to = aws_s3_bucket_ownership_controls.deploy
  id = "battle-royal-deploy-495791792486"
}

import {
  to = aws_iam_openid_connect_provider.github
  id = "arn:aws:iam::495791792486:oidc-provider/token.actions.githubusercontent.com"
}
import {
  to = aws_iam_role.deploy
  id = "deploy"
}
import {
  to = aws_iam_role_policy.deploy
  id = "deploy:deployPolicy"
}

import {
  to = aws_iam_role.ec2
  id = "battle-royal-ec2"
}
import {
  to = aws_iam_role_policy.ec2_read_releases
  id = "battle-royal-ec2:read-releases"
}
import {
  to = aws_iam_role_policy_attachment.ec2_ssm
  id = "battle-royal-ec2/arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}
import {
  to = aws_iam_instance_profile.ec2
  id = "battle-royal-ec2"
}

import {
  to = aws_sns_topic.alerts
  id = "arn:aws:sns:ap-northeast-2:495791792486:battle-royal-alerts"
}
import {
  to = aws_sns_topic_subscription.alerts_email
  id = "arn:aws:sns:ap-northeast-2:495791792486:battle-royal-alerts:5df1d1dc-4632-4b34-aaf3-6507d2f50459"
}

# Moved here from infra/prod on 2026-10-05 so a rebuilt server keeps the address.
import {
  to = aws_eip.prod
  id = "eipalloc-082c75d049e9df296"
}
