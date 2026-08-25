data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_instance" "app" {
  ami                    = data.aws_ami.amazon_linux.id
  instance_type          = var.instance_type
  key_name               = var.key_pair_name
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.app.id]

  user_data = templatefile("${path.module}/user-data.sh", {
    db_password       = var.db_password
    ecr_registry      = var.ecr_registry
    aws_region        = var.aws_region
    cognito_pool_id   = aws_cognito_user_pool.main.id
    cognito_client_id = aws_cognito_user_pool_client.app.id
  })

  iam_instance_profile = aws_iam_instance_profile.app.name

  root_block_device {
    volume_size = 30
    volume_type = "gp3"
  }

  tags = { Name = "ecommerce-app" }
}

resource "aws_eip" "app" {
  instance = aws_instance.app.id
  domain   = "vpc"

  tags = { Name = "ecommerce-eip" }
}
