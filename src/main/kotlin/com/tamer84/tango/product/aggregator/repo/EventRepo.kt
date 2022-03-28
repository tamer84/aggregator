package com.tamer84.tango.product.aggregator.repo

import com.tamer84.tango.product.aggregator.domain.AwsTangoEvent
import com.tamer84.tango.product.aggregator.domain.deserializeEvent
import com.tamer84.tango.product.aggregator.util.EnvVar.EVENT_TABLE
import com.tamer84.tango.product.aggregator.util.EnvVar.EVENT_TABLE_PRODUCT_INDEX
import com.tamer84.tango.product.aggregator.util.EnvVar.EVENT_TABLE_SAGA_INDEX
import com.tamer84.tango.product.aggregator.util.Metrics
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.QueryResponse
import software.amazon.awssdk.services.dynamodb.model.Select
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.stream.Collectors


//https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.OperatorsAndFunctions.html
class EventRepo(private val dynamoDb: DynamoDbClient) {

    companion object {
        private val log = LoggerFactory.getLogger(EventRepo::class.java)
        private val paginatedTimer = Metrics.createTimer("dynamo", "queryPaginator.duration")

        // Table and Query constants
        const val COL_ID          = "unique_id"
        const val COL_PAYLOAD     = "payload"
        const val COL_TIMESTAMP   = "timestamp"
        const val TIMESTAMP_ALIAS = "#t"
        const val COL_SAGA_ID     = "saga_id"
    }

    fun fetchIngressTime(sagaId: String) : Long? {

        val qBegin = Instant.now()

        val request =  QueryRequest.builder()
            .tableName(EVENT_TABLE)
            .indexName(EVENT_TABLE_SAGA_INDEX)
            .eventsSagaQueryExpressions(sagaId, limit = 1)
            .consistentRead(false)  // much better performance
            .scanIndexForward(true) // true is ascending (for some reason better performance)
            .build()

        val response = dynamoDb.queryPaginator(request)

        val duration = Duration.between(qBegin, Instant.now())

        paginatedTimer.update(duration)

        log.info("First event ingress time query [durationMs=${duration.toMillis()}]")

        return response
            .flatMap { toTangoEventsParallel(it) }
            .map { it?.timestamp }
            .firstOrNull()
    }

    fun fetchEventCount(sagaId: String) : Int {
        val qBegin = Instant.now()

        val request = QueryRequest.builder()
            .tableName(EVENT_TABLE)
            .indexName(EVENT_TABLE_SAGA_INDEX)
            .eventsSagaQueryExpressions(sagaId = sagaId)
            .consistentRead(false)  // much better performance
            .scanIndexForward(true) // true is ascending (for some reason better performance)
            .select(Select.COUNT)
            .build()

        val responses = dynamoDb.query(request)

        val duration = Duration.between(qBegin, Instant.now())

        paginatedTimer.update(duration)

        log.info("Event Count query [durationMs=${duration.toMillis()}, events=${responses.count()}]")

        return responses.count()
    }

    fun fetchEventsByProductId(productId: UUID, from : Long? = null) : List<AwsTangoEvent> {

        val qBegin = Instant.now()
        log.info("Table name : $EVENT_TABLE")
        val request = QueryRequest.builder()
                .tableName(EVENT_TABLE)
                .indexName(EVENT_TABLE_PRODUCT_INDEX)
                .eventsQueryExpressions(productId,from)
                .consistentRead(false)  // much better performance
                .scanIndexForward(true) // true is ascending (for some reason better performance)
                .select(Select.SPECIFIC_ATTRIBUTES)
                .expressionAttributeNames(mapOf(TIMESTAMP_ALIAS to COL_TIMESTAMP)) // this is required because timestamp is a DynamoDB keyword
                .projectionExpression("$COL_ID, $TIMESTAMP_ALIAS, $COL_PAYLOAD") // the attributes to retrieve (fields)
                .build()


        val responses = dynamoDb.queryPaginator(request)

        val events = responses.flatMap { toTangoEventsParallel(it) }
                .filterNotNull()
                // we must sort by timestamp because the results are not returned in order
                .sortedBy { it.timestamp }

        val duration = Duration.between(qBegin, Instant.now())

        paginatedTimer.update(duration)

        log.info("Queries+Transform [durationMs=${duration.toMillis()}, events=${events.size}]")

        return events
    }

    // in tests, parallelStream was faster than async, flow, and sequences
    private fun toTangoEventsParallel(response: QueryResponse) : List<AwsTangoEvent?> {
        return response.items()
                .parallelStream()
                .map(this::toTangoEvent).collect(Collectors.toList())
    }

    private fun toTangoEvent(data : Map<String, AttributeValue>) : AwsTangoEvent? {

        // these fields should always be present
        val payload   = data[COL_PAYLOAD]!!.s()
        val timestamp = data[COL_TIMESTAMP]!!.n().toLong()
        val eventId   = data[COL_ID]!!.s()
        val event     = deserializeEvent(payload) ?: return null

        return AwsTangoEvent(timestamp, eventId, event)
    }
}
