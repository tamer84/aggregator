locals {
  snapshot_event_threshold   = 50
  snapshot_event_time_buffer = 5 // Seconds
  env_variables = [
    {
      "name"  = "PORT",
      "value" = "7000"
    },
    {
      "name"  = "EVENT_TABLE",
      "value" = "product-events-${terraform.workspace}"
    },
    {
      "name"  = "EVENT_TABLE_INDEX",
      "value" = "product_id-index"
    },
    {
      "name"  = "EVENT_TABLE_PARTITION_KEY",
      "value" = "product_id"
    },
    {
      "name"  = "EVENT_TABLE_SAGA_INDEX",
      "value" = "saga_id-index"
    },
    {
      "name"  = "SNAPSHOT_DISABLE_UPDATES",
      "value" = "false"
    },
    {
      "name"  = "SNAPSHOT_TABLE_NAME",
      "value" = local.tableName
    },
    {
      "name"  = "SNAPSHOT_TABLE_PRIMARY_KEY",
      "value" = local.primaryKey
    },
    {
      "name"  = "SNAPSHOT_EVENT_THRESHOLD",
      "value" = local.snapshot_event_threshold
    },
    {
      "name"  = "SNAPSHOT_EVENT_TIME_BUFFER",
      "value" = local.snapshot_event_time_buffer
    }
  ]
  application_name           = "product-aggregator"
  application_container_name = "${local.application_name}-${terraform.workspace}"
  aws_log_group_name         = "/ecs/${local.application_container_name}"

  load_balancer = {
    lb_type = "network",
    listeners = {
      "tls" = {
        "protocol"        = "TLS",
        "port"            = "443",
        "certificate_arn" = data.terraform_remote_state.account_resources.outputs.certificate.arn
      }
    }
  }

  container_definitions = {
    "product-aggregator" : module.aggregator.container_definitions
  }

  cpu_size_at_service_level = 1024
  mem_size_at_service_level = 2048
  cpu_size_at_container_level = 512
  mem_size_at_container_level = 1024

  ecs_services = {
    "product-aggregator" = {

      # The number of containers of the specified task definition to place and keep running.
      "desired_count" = 1,

      "target_group" = {
        "priority"         = 1,
        "action"           = "forward",
        "protocol"         = "TCP",
        "application_port" = 7000,
        "health_endpoint"  = "/ping",
        "path"             = "" //Not used for network load balancers
      },
      "task_role" = data.terraform_remote_state.environment_resources.outputs.dynamodb_access_role.arn,

      # Below are CPU and MEMORY fields required by the Fargate.
      # Fargate deployment requires task CPU and MEMORY configurations
      # because that is the billable construct for Fargate.

      # Amount of CPU reserved for the task.
      #
      # A service can have multiple running containers for a task.
      # This is the CPU reserved to the whole set of running containers.
      "cpu" = local.cpu_size_at_service_level,

      # Amount of MEM reserved for the task.
      #
      # A service can have multiple running containers for a task.
      # This is the MEM reserved to the whole set of running containers.
      "memory" = local.mem_size_at_service_level
    }
  }
}

module "fargate" {
  source = "git::ssh://git@github.com/tamer84/infra.git//modules/fargate?ref=develop"

  cluster_name     = "product-aggregator"
  dns_namespace_id = data.terraform_remote_state.environment_resources.outputs.private-dns-namespace.id

  fargate_security_groups  = [data.terraform_remote_state.environment_resources.outputs.group_internal_access.id]
  private_subnets          = data.terraform_remote_state.environment_resources.outputs.private-subnet.*.id
  public_subnets           = data.terraform_remote_state.environment_resources.outputs.public-subnet.*.id
  lb_listeners             = local.load_balancer.listeners
  lb_type                  = local.load_balancer.lb_type
  vpc_id                   = data.terraform_remote_state.environment_resources.outputs.vpc.id
  ecs_services             = local.ecs_services
  container_definitions    = local.container_definitions
  is_publicly_accessible   = false
  zone_id                  = data.terraform_remote_state.account_resources.outputs.dns.zone_id
  default_ecs_service_name = "product-aggregator"
}

# container_definitions
module "aggregator" {
  source  = "mongodb/ecs-task-definition/aws"
  version = "2.1.0"

  name        = local.application_container_name
  links       = []
  environment = local.env_variables

  logConfiguration = {
    "logDriver" = "awslogs",
    "options" = {
      "awslogs-group"         = local.aws_log_group_name,
      "awslogs-region"        = "eu-central-1",
      "awslogs-stream-prefix" = "ecs"
    }
  }

  image     = "${aws_ecr_repository.ecr_repo.repository_url}:latest"
  essential = true

  portMappings = [
    {
      containerPort = 7000
      hostPort      = 7000
    },
  ]

  # Amount of CPU reserved for each container.
  #
  # A task can have several running containers.
  cpu = local.cpu_size_at_container_level

  # Amount of MEM reserved for each container.
  #
  # A task can have several running containers.
  memory = local.mem_size_at_container_level

  family = "Tango"

  tags = {
    Terraform   = "true"
    Environment = terraform.workspace
    Product     = "Tango"
    Name        = local.application_container_name
    Application = local.application_name
    Role        = "aggregation"
    Public      = "false"
    Core        = "true"
  }
}

#Autoscaling
resource "aws_appautoscaling_target" "ecs_target" {
  max_capacity       = 2
  min_capacity       = 1
  resource_id        = "service/${module.fargate.ecs_cluster.name}/${module.fargate.ecs_service.product-aggregator.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "ecs_policy" {
  name               = "Product-Aggregator-AutoScaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.ecs_target.resource_id
  scalable_dimension = aws_appautoscaling_target.ecs_target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs_target.service_namespace

  target_tracking_scaling_policy_configuration {
    target_value       = 85
    scale_in_cooldown  = 300
    scale_out_cooldown = 300
    disable_scale_in   = false

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}

# Route53 URL
output "route53_record" {
  value = module.fargate.route53_record
}
