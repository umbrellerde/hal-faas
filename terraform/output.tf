output "sut_ip" {
    value = aws_instance.sut.public_dns
}