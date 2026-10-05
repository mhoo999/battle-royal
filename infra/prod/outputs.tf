output "public_ip" {
  value = aws_eip.server.public_ip
}

output "db_endpoint" {
  value = aws_db_instance.main.address
}
