# Aggregator
The event aggregator application

## Technologies

Below are the core technologies used

* Maven
* JDK 11
* [Kotlin 1.5](https://kotlinlang.org/docs/reference/)
* [Docker](https://hub.docker.com/r/adoptopenjdk/openjdk11/)
* [Terraform](https://www.terraform.io/)

## Libraries

Below are the core libraries used

* [Javalin](https://javalin.io/)
* [AWS SDK 2](https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/welcome.html)
* [DynamoDB](https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/examples-dynamodb.html)

## Build Instructions

Maven Build

```shell script
mvn clean package
```

Docker Build

```shell script
docker build -t connect-aggregator:latest .
```

## Run Instructions

Docker Run

```shell script
docker run -p 7000:7000 --rm col-aggregator:latest
```

Docker-Compose Run

```shell script
docker-compose up
```

## Deployment

Deploy this application as an AWS Fargate Container using [Terraform](https://www.terraform.io/docs/providers/aws/index.html)

### From Local

```shell script
terraform init
```
```shell script
terraform workspace new dev
```

```shell script
terraform workspace select dev
```

```shell script
terraform plan
```

#### terraform apply

After analyzing the plan, you can apply this changes. 

WARNING: Please be careful, this process should optimally be done exclusively from CICD
```shell script
terraform apply
```

