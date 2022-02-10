terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 3.74"

    }
  }
}

provider "aws" {
  profile = "default"
  region  = var.aws_region
}

// ----
// VPC
// ----
data "aws_availability_zones" "available" {}

module "vpc" {
  source          = "terraform-aws-modules/vpc/aws"
  name            = "${var.namespace}-vpc"
  cidr            = "10.0.0.0/16"
  azs             = data.aws_availability_zones.available.names
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24"]
  #assign_generated_ipv6_cidr_block = true
  create_database_subnet_group = true
  enable_nat_gateway           = true
  single_nat_gateway           = true
}

// Allow ssh from anywhere
resource "aws_security_group" "allow_ssh_pub" {
  name        = "${var.namespace}-allow_ssh"
  description = "Allow SSH inbound traffic"
  vpc_id      = module.vpc.vpc_id

  ingress {
    description = "SSH from the internet"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.namespace}-allow_ssh_pub"
  }
}

// ----
// EC2 instances
// ----
resource "aws_instance" "sut" {
  // Ubuntu in eu-central-1
  ami                       = "ami-0d527b8c289b4af7f"
  instance_type             = var.instance_type_sut
  key_name                  = var.key_name
  subnet_id                 = module.vpc.public_subnets[0]
  vpcvpc_security_group_ids = [aws_security_group.allow_ssh_pub.security_group_id]

  tags = {
    "Name" = "${var.namespace}-sut"
  }

  // Copy the node manager executable onto the machine
  provisioner "file" {
    source      = "../noma/target/noma-1.0-SNAPSHOT-jar-with-dependencies.jar"
    destination = "~/noma.jar"
    connection {
      type        = "ssh"
      user        = "ubuntu"
      private_key = locals.private_ssh
      host        = self.public_dns
    }
  }

  // Install Updates / Pipenv / ...
  provisioner "remote-exec" {
    inline = [
      "sudo apt-get update -qq",
      "sudo apt-get -qq install default-jre python-pip",
      "pip install --user pipenv"
    ]
    connection {
      type        = "ssh"
      user        = "ubuntu"
      private_key = locals.private_ssh
      host        = self.public_dns
    }
  }
  #   provisioner "file" {
  #     source      = "../benchmark-client/target/benchmark-client-1.0-SNAPSHOT-jar-with-dependencies.jar"
  #     destination = "~/benchmark-client.jar"
  #   }
}
