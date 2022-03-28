# ========================================
# CICD
# ========================================
locals {
  connect_aggregator_imagedef = "product-aggregator-imagedefinitions.json"
}

module "cicd" {
  source = "git::ssh://git@github.com/tamer84/infra.git//modules/cicd?ref=develop"

  codestar_connection_arn = data.terraform_remote_state.account_resources.outputs.git_codestar_conn.arn

  pipeline_base_configs = {
    "name"        = "${var.application_name}-${terraform.workspace}"
    "bucket_name" = data.terraform_remote_state.environment_resources.outputs.cicd_bucket.id
    "role_arn"    = data.terraform_remote_state.account_resources.outputs.cicd_role.arn
  }

  codebuild_build_stage = {
    "project_name"        = "${var.application_name}-${terraform.workspace}"
    "github_branch"       = contains(["dev", "test", "int"], terraform.workspace) ? "develop" : "main"
    "github_repo"         = "tamer84/aggregator"
    "github_access_token" = data.terraform_remote_state.account_resources.outputs.github_access_token
    "github_certificate"  = "${data.terraform_remote_state.environment_resources.outputs.cicd_bucket.arn}/${data.terraform_remote_state.environment_resources.outputs.github_cert.id}"

    "service_role_arn"   = data.terraform_remote_state.account_resources.outputs.cicd_role.arn
    "cicd_bucket_id"     = data.terraform_remote_state.environment_resources.outputs.cicd_bucket.id
    "vpc_id"             = data.terraform_remote_state.environment_resources.outputs.vpc.id
    "subnets_ids"        = data.terraform_remote_state.environment_resources.outputs.private-subnet.*.id
    "security_group_ids" = [data.terraform_remote_state.environment_resources.outputs.group_internal_access.id]

    "docker_img_url"                   = data.terraform_remote_state.terraform_build_image_resources.outputs.ecr_repository.repository_url
    "docker_img_tag"                   = "latest"
    "docker_img_pull_credentials_type" = "SERVICE_ROLE"
    "buildspec"                        = "./buildspec.yml"
    "env_vars" = [
      {
        name  = "ENVIRONMENT"
        value = terraform.workspace
      },
      {
        name  = "AWS_ACCOUNT_ID"
        value = "802306197541"
      },
      {
        name  = "IMAGE_TAG"
        value = "latest"
      },
      {
        name  = "IMAGE_REPO_NAME"
        value = aws_ecr_repository.ecr_repo.name
      },
      {
        name  = "ARTIFACT_NAME"
        value = local.connect_aggregator_imagedef
      }
    ]
  }

  deploy_stage_ecs = {
    "name"              = "Deploy"
    "ecs_cluster_arn"   = module.fargate.ecs_cluster.arn
    "ecs_service_id"    = module.fargate.ecs_service["product-aggregator"].id
    "imagedef_filename" = local.connect_aggregator_imagedef
  }
}
