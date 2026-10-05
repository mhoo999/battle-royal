output "instance_profile" {
  value = aws_iam_instance_profile.ec2.name
}

output "alerts_topic_arn" {
  value = aws_sns_topic.alerts.arn
}

output "release_bucket" {
  value = aws_s3_bucket.deploy.bucket
}

output "prod_eip_allocation_id" {
  value = aws_eip.prod.id
}

output "prod_public_ip" {
  value = aws_eip.prod.public_ip
}
