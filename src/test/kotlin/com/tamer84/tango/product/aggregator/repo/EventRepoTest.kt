package com.tamer84.tango.product.aggregator.repo

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.tamer84.tango.icecream.domain.pricing.event.PricingUpdatedEvent
import com.tamer84.tango.model.Market
import com.tamer84.tango.product.aggregator.util.JsonUtil
import org.junit.Assert.assertTrue
import org.junit.Test
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.QueryResponse
import software.amazon.awssdk.services.dynamodb.paginators.QueryIterable
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertEquals


class EventRepoTest {

    private val dynamoClient = mock<DynamoDbClient>()

    private val eventRepo = EventRepo(dynamoClient)

    @Test
    fun testEventOrderIsAscending() {

        val productId = UUID.randomUUID()

        // Step 1: events with shuffled timestamps
        val events = (0..10).map { i ->
            val timestamp = Instant.now().minus(3, ChronoUnit.DAYS).plus(i.toLong(), ChronoUnit.HOURS).toEpochMilli()
            PricingUpdatedEvent(productId,"$i","test_domain", Market.FR, timestamp)
        }.shuffled()

        // Step 2: convert events to the data structure that Dynamo uses to return a row from the table
        val items : List<Map<String,AttributeValue>> = events.map { e ->
            mapOf(
                    EventRepo.COL_PAYLOAD to AttributeValue.builder().s(JsonUtil.toJson(e)).build(),
                    EventRepo.COL_TIMESTAMP to AttributeValue.builder().n(e.timestamp.toString()).build(),
                    EventRepo.COL_ID to AttributeValue.builder().s(UUID.randomUUID().toString()).build()
            )
        }

        // Step 3: mock the QueryResponse.  This contains the iterator that should return the items from Step 2
        val queryResponse : QueryResponse = mock()
        whenever(queryResponse.items()).thenReturn(items)

        // Step 4: mock the QueryIterable.  This is the response from Dynamo.queryPaginator
        val queryIterable : QueryIterable = mock()
        whenever(queryIterable.iterator()).thenReturn(mutableListOf(queryResponse).iterator())

        // GIVEN
        whenever(dynamoClient.queryPaginator(any<QueryRequest>())).thenReturn(queryIterable)

        // WHEN
        val data = eventRepo.fetchEventsByProductId(productId)

        // THEN
        assertEquals(11, data.size, "Make sure all events returned")

        for(i in 1 until data.size) {
            val prev = i-1
            assertTrue("Column.timestamp should be earlier", data[prev].timestamp < data[i].timestamp)
            assertTrue("Event.timestamp should be earlier", data[prev].payload.timestamp < data[i].payload.timestamp)
            assertTrue("Event.sagaId should be earlier", data[prev].payload.sagaId.toInt() < data[i].payload.sagaId.toInt())
        }
    }
}
