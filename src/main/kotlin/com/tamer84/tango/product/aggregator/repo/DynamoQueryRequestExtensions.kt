package com.tamer84.tango.product.aggregator.repo

import com.tamer84.tango.product.aggregator.util.EnvVar
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import java.util.*

/**
 * Events that are not part of the Aggregate that are found in the event_name column
 */
private val IGNORE_EVENTS = setOf<String>()

/**
 * Create all of the required query and filter expressions for getting events from the event table
 * by productId and from a given time.
 */
fun QueryRequest.Builder.eventsQueryExpressions(
    productId: UUID, from : Long? = null, sagaId : String? = null, limit : Int? = null) : QueryRequest.Builder {

    val attributeValues = hashMapOf(":v" to DynamoUtil.strAttr(productId))

    //////  QUERY BY PARTITION KEY AND SORT KEY
    var partitionKeyExpression = "${EnvVar.EVENT_TABLE_PARTITION_KEY} =:v"

    if (from != null){
        partitionKeyExpression += " AND ${EventRepo.TIMESTAMP_ALIAS} >= :tsFrom"
        attributeValues[":tsFrom"] = DynamoUtil.numAttr(from)
    }

    //////  ADD FILTER FOR EVENTS TO IGNORE
//    val notInEvents = IGNORE_EVENTS.withIndex().joinToString {
//        val key = ":not${it.index}"
//        attributeValues[key] = DynamoUtil.strAttr(it.value)
//        key
//    }

    var filterExpression : String? = null

    sagaId?.let {
        filterExpression = " AND ${EventRepo.COL_SAGA_ID} =:sagaId "
        attributeValues[":sagaId"] = DynamoUtil.strAttr(sagaId)

    }

    val query = this.keyConditionExpression(partitionKeyExpression)
        .expressionAttributeValues(attributeValues)
    filterExpression?.let { query.filterExpression(it)  }
    //.filterExpression(filterExpression) // not equal to

    return limit?.let {
        query.limit(it)
    } ?: query
}

fun QueryRequest.Builder.eventsSagaQueryExpressions(sagaId : String, from : Long? = null, limit : Int? = null) : QueryRequest.Builder {

    val attributeValues = hashMapOf(":s" to DynamoUtil.strAttr(sagaId))

    //////  QUERY BY PARTITION KEY AND SORT KEY
    var primaryKeyExpression = "saga_id =:s"

    if (from != null){
        primaryKeyExpression += " AND ${EventRepo.TIMESTAMP_ALIAS} >= :tsFrom"
        attributeValues[":tsFrom"] = DynamoUtil.numAttr(from)
    }

    //////  ADD FILTER FOR EVENTS TO IGNORE
    val notInEvents = IGNORE_EVENTS.withIndex().joinToString {
        val key = ":not${it.index}"
        attributeValues[key] = DynamoUtil.strAttr(it.value)
        key
    }

    val filterExpression = "NOT (event_name IN ($notInEvents))"

    val query = this.keyConditionExpression(primaryKeyExpression)
        .filterExpression(filterExpression) // not equal to
        .expressionAttributeValues(attributeValues)

    return limit?.let {
        query.limit(it)
    } ?: query
}
