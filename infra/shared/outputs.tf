output "instance_profile" {
  value = aws_iam_instance_profile.ec2.name
}

output "alerts_topic_arn" {
  value = aws_sns_topic.alerts.arn
}

output "release_bucket" {
  value = aws_s3_bucket.deploy.bucket
}
