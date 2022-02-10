output "sut_ip" {
    value = aws_instance.sut.public_dns
}

output "sut_connection_string" {
    value       = "ssh ubuntu@${aws_instance.sut.public_ip}"
}