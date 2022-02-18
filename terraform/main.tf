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

locals {
  private_ssh = file("~/.ssh/id_ed25519")
  public_ssh  = file("~/.ssh/id_ed25519.pub")
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
  public_subnets  = ["10.0.101.0/24"]
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

  ingress {
    description = "Traffic inbetween"
    from_port   = 0
    to_port     = 20000
    protocol    = "tcp"
    self        = true
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

resource "aws_key_pair" "key" {
  key_name   = "${var.namespace}-main-key"
  public_key = local.public_ssh
}

// ----
// EC2 instances
// ----
resource "aws_instance" "sut" {
  count = 1
  // Ubuntu in eu-central-1
  //ami                    = "ami-0d527b8c289b4af7f"
  // Deep Learning AMI
  ami                    = "ami-030544fb939a57d47"
  instance_type          = var.instance_type_sut
  key_name               = aws_key_pair.key.key_name
  subnet_id              = module.vpc.public_subnets[0]
  vpc_security_group_ids = [aws_security_group.allow_ssh_pub.id]

  tags = {
    "Name" = "${var.namespace}-sut"
  }

  # provisioner "file" {
  #   source      = "~/git/umbrellerde/hal-faas"
  #   destination = "/home/ubuntu/hal-faas"

  #   connection {
  #     type        = "ssh"
  #     user        = "ubuntu"
  #     private_key = local.private_ssh
  #     host        = self.public_ip
  #   }
  # }

    // Install Updates / Pipenv / ...
  provisioner "remote-exec" {
    inline = [
      "sudo apt-get -qq update",
      // TODO Package default-jre has no installation candidate
      "sudo apt-get -qq install openjdk-11-jre",
      "source activate pytorch_p37", 
      "pip install -q --user pipenv ray tensorboardx",
      "export PATH=$PATH:/home/ubuntu/.local/bin",
      "pip --version",
      "pipenv --version",
      "java -version",
      "cd ml_benchmark && python setup.py install"
    ]
    connection {
      type        = "ssh"
      user        = "ubuntu"
      private_key = local.private_ssh
      host        = self.public_ip
    }
  }

  // Copy the base folder over to the remote host so that everything is there
  // This must run after the remote-exec as only remote exec can wait until ssh is up
  provisioner "local-exec" {
    working_dir = "../"
    command = "rsync -r . ubuntu@${self.public_ip}:/home/ubuntu/hal-faas"
  }

  // Copy the node manager executable onto the machine
  provisioner "file" {
    source      = "../noma/target/noma-1.0-SNAPSHOT-jar-with-dependencies.jar"
    destination = "/home/ubuntu/hal-faas/noma.jar"

    connection {
      type        = "ssh"
      user        = "ubuntu"
      private_key = local.private_ssh
      host        = self.public_ip
    }
  }

  provisioner "file" {
    source      = "~/git/gebauerm/ml_benchmark"
    destination = "/home/ubuntu/ml_benchmark"

    connection {
      type        = "ssh"
      user        = "ubuntu"
      private_key = local.private_ssh
      host        = self.public_ip
    }
  }
}

resource "aws_instance" "bedrock" {
  // Ubuntu in eu-central-1
  count                  = 0
  ami                    = "ami-0d527b8c289b4af7f"
  instance_type          = var.instance_type_bedrock
  key_name               = aws_key_pair.key.key_name
  subnet_id              = module.vpc.public_subnets[0]
  vpc_security_group_ids = [aws_security_group.allow_ssh_pub.id]
  private_ip             = "10.0.101.101"

  tags = {
    "Name" = "${var.namespace}-bedrock"
  }


  // Install Bedrock and run it
  provisioner "remote-exec" {
    inline = [
      "sudo apt-get -qq update",
      "sudo apt-get -qq upgrade",
      "sudo apt-get -qq dist-upgrade",
      "sudo apt-get -qq update",
      "sudo apt-get -qq install wget libpcrecpp0v5",
      "wget https://github.com/Expensify/Bedrock/releases/download/2020-11-16/bedrock",
      "touch bedrock.db",
      "chmod +x bedrock",
      // The IP Addr of the bedrock instance is always the same
      "./bedrock -nodeHost 10.0.101.101:8888 &"
    ]
    connection {
      type        = "ssh"
      user        = "ubuntu"
      private_key = local.private_ssh
      host        = self.public_ip
    }
  }
}

resource "aws_instance" "bench" {
  // Ubuntu in eu-central-1
  count                  = 0
  ami                    = "ami-0d527b8c289b4af7f"
  instance_type          = var.instance_type_bench
  key_name               = aws_key_pair.key.key_name
  subnet_id              = module.vpc.public_subnets[0]
  vpc_security_group_ids = [aws_security_group.allow_ssh_pub.id]

  tags = {
    "Name" = "${var.namespace}-bench"
  }


  // Copy the node manager executable onto the machine
  provisioner "file" {
    source      = "../benchmark-client/target/benchmark-client-1.0-SNAPSHOT-jar-with-dependencies.jar"
    destination = "/home/ubuntu/benchmark-client.jar"

    connection {
      type        = "ssh"
      user        = "ubuntu"
      private_key = local.private_ssh
      host        = self.public_ip
    }
  }

  // Install Updates / Pipenv / ...
  provisioner "remote-exec" {
    inline = [
      "sudo apt-get -qq update",
      // TODO Package default-jre has no installation candidate
      "sudo apt-get -qq install openjdk-11-jre",
      "java -version",
    ]
    connection {
      type        = "ssh"
      user        = "ubuntu"
      private_key = local.private_ssh
      host        = self.public_ip
    }
  }
}
