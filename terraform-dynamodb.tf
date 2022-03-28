locals {
  tableName  = "product-aggregatorSnapshot-${terraform.workspace}"
  primaryKey = "product_id"
}

resource "aws_dynamodb_table" "aggregator_snapshot" {
  name         = local.tableName
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = local.primaryKey

  attribute {
    name = local.primaryKey
    type = "S"
  }
}
