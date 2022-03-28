# ECS Repository for container images
resource "aws_ecr_repository" "ecr_repo" {
  name                 = "product-aggregator-${terraform.workspace}"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Terraform = "true"
  }
}
