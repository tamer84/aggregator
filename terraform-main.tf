# ========================================
# Initialization
# ========================================
terraform {
  // Declares where terraform stores the application state
  backend "s3" {
    encrypt = "true"
    bucket  = "tango-terraform"
    // terraform does not support variables on the init, you need to put the app name below
    key            = "resources/aggregator/tfstate.tf"
    region         = "eu-central-1"
    dynamodb_table = "terraform"
  }
}

provider "aws" {
  // Use the AWS provider from terraform https://www.terraform.io/docs/providers/aws/index.html
  region = "eu-central-1"
}

provider "github" {
  token        = data.terraform_remote_state.account_resources.outputs.github_access_token
}

data "terraform_remote_state" "account_resources" {
  // Imports the account resources to use the shared information
  backend = "s3"
  config = {
    encrypt = "true"
    bucket  = "tango-terraform"
    key     = "account_resources/tfstate.tf"
    region  = "eu-central-1"
  }
  workspace = "default"
}

data "terraform_remote_state" "environment_resources" {
  // Imports the environment specific resources to use the shared information
  backend = "s3"
  config = {
    encrypt = "true"
    bucket  = "tango-terraform"
    key     = "environment_resources/tfstate.tf"
    region  = "eu-central-1"
  }
  workspace = terraform.workspace
}

data "terraform_remote_state" "terraform_build_image_resources" {
  backend = "s3"
  config = {
    encrypt = "true"
    bucket  = "tango-terraform"
    key     = "resources/terraform-build-image/tfstate.tf"
    region  = "eu-central-1"
  }
  workspace = terraform.workspace
}

data "aws_caller_identity" "current" {}
