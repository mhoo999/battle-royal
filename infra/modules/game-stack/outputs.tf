output "instance_id" {
  value = aws_instance.server.id
}

output "public_ip" {
  description = "The kept IP if one is attached, else the server's own"
  value       = var.eip_allocation_id == null ? aws_instance.server.public_ip : var.public_ip
}

output "db_endpoint" {
  value = aws_db_instance.main.address
}
