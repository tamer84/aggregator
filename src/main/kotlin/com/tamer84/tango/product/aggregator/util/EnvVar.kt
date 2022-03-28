package com.tamer84.tango.product.aggregator.util

object EnvVar {

    val PORT                       = System.getenv("PORT")?.toIntOrNull() ?: 7001
    val EVENT_TABLE                = System.getenv("EVENT_TABLE") ?: "product-events-dev"
    val EVENT_TABLE_PRODUCT_INDEX  = System.getenv("EVENT_TABLE_INDEX") ?: "product_id-index"
    val EVENT_TABLE_SAGA_INDEX     = System.getenv("EVENT_TABLE_SAGA_INDEX") ?: "saga_id-index"
    val EVENT_TABLE_PARTITION_KEY  = System.getenv("EVENT_TABLE_PARTITION_KEY") ?: "product_id"
    // Buffer allows including events in the aggregation occurring x seconds before the last snapshot event.
    val SNAPSHOT_EVENT_TIME_BUFFER = System.getenv("SNAPSHOT_EVENT_TIME_BUFFER")?.toIntOrNull() ?: 1
    val SNAPSHOT_DISABLE_UPDATES   = System.getenv("SNAPSHOT_DISABLE_UPDATES")?.toBoolean() ?: false
    val SNAPSHOT_EVENT_THRESHOLD   = System.getenv("SNAPSHOT_EVENT_THRESHOLD")?.toIntOrNull() ?: 30
    val SNAPSHOT_TABLE             = System.getenv("SNAPSHOT_TABLE_NAME") ?: "product-aggregatorSnapshot-dev"
    val SNAPSHOT_TABLE_PRIMARY_KEY = System.getenv("SNAPSHOT_TABLE_PRIMARY_KEY") ?: "product_id"
}
