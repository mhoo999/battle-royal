output "public_ip" {
  value = module.stack.public_ip
}

output "db_endpoint" {
  value = module.stack.db_endpoint
}

output "instance_id" {
  value = module.stack.instance_id
}
