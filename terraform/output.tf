output "sut_ip" {
    value = [for instance in aws_instance.sut: instance.public_dns]
}

output "sut_connection_string" {
    value       = [for instance in aws_instance.sut: "ssh ubuntu@${instance.public_ip}"]
}

output "bedrock_pub_ip" {
    value = aws_instance.bedrock.public_ip
}

output "benchmarker_connection_string" {
    value = "ssh ubuntu@${aws_instance.bench.public_ip}"
}