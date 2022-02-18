output "sut_ip" {
    value = [for instance in aws_instance.sut: instance.public_dns]
}

output "sut_connection_string" {
    value       = [for instance in aws_instance.sut: "ssh ubuntu@${instance.public_ip}"]
}

output "bedrock_pub_ip" {
    value = [for instance in aws_instance.bedrock: "ssh ubuntu@${instance.public_ip}"]
}

output "benchmarker_connection_string" {
    value = [for instance in aws_instance.bench: "ssh ubuntu@${instance.public_ip}"]
}