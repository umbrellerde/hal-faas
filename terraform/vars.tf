variable "instance_type_sut" {
  description = "Type of the sut instances"
  default     = "inf1.xlarge"
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

variable "namespace" {
  default = "hal-faas"
}