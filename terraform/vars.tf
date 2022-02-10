variable "instance_type_sut" {
  description = "Type of the sut instances"
  default     = "t2.micro"
}

variable "instance_type_bedrock" {
  description = "Type of the bedrock instance"
  default     = "t2.micro"
}

variable "instance_type_bench" {
  description = "Type of the benchmarking instance"
  default     = "t2.micro"
}

variable "aws_region" {
  type    = string
  default = "eu-central-1"
}

locals {
  private_ssh = file("~/.ssh/id_ed25519")
}

variable "namespace" {
  default = "hal-faas"
}

variable "key_name" {
  description = "AWS Key to use for Instances"
  default = "????"
}