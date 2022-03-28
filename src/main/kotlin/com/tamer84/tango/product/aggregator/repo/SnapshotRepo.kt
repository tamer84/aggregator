package com.tamer84.tango.product.aggregator.repo

import com.tamer84.tango.icecream.domain.icsi.model.IceCreamStockItem
import com.tamer84.tango.product.aggregator.domain.AggregateSnapshotEntity
import com.tamer84.tango.product.aggregator.domain.SaveSnapshotResponse
import com.tamer84.tango.product.aggregator.repo.DynamoUtil.numAttr
import com.tamer84.tango.product.aggregator.repo.DynamoUtil.strAttr
import com.tamer84.tango.product.aggregator.util.EnvVar
import com.tamer84.tango.product.aggregator.util.JsonUtil
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.lang.System.currentTimeMillis
import java.util.*

data class SnapshotMetadata(val lastEventId: String, val lastEventTimestamp: Long, val numOfNewEvents: Int)

class SnapshotRepo(private val dynamoDb :DynamoDbClient) {

    companion object {
        private val log = LoggerFactory.getLogger(SnapshotRepo::class.java)

        private val TABLE_SNAPSHOT = EnvVar.SNAPSHOT_TABLE
        private val PRIMARY_KEY    = EnvVar.SNAPSHOT_TABLE_PRIMARY_KEY

        private const val COL_CREATED_TIME      = "createdTime"
        private const val COL_LATEST_EVENT_ID   = "latestEventId"
        private const val COL_LATEST_EVENT_TIME = "latestEventTime"
        private const val COL_CSI               = "connectStockItem"
    }

    fun fetch(productId: UUID) : AggregateSnapshotEntity? {

        val request =  GetItemRequest.builder()
                .tableName(TABLE_SNAPSHOT)
                .key(mapOf(PRIMARY_KEY to strAttr(productId)))
                .build()
        val response = dynamoDb.getItem(request)

        return when(response.hasItem()) {
            true -> toAggregateSnapshotEntity(response.item())
            else -> { log.warn("Snapshot not found"); null }
        }
    }

    fun save(item : IceCreamStockItem, metadata: SnapshotMetadata) : SaveSnapshotResponse {

        requireNotNull(item.id) {"ConnectStockItem id is missing"}

        val snapshot = AggregateSnapshotEntity(
                UUID.fromString(item.id),
                item,
                currentTimeMillis(),
                metadata.lastEventId,
                metadata.lastEventTimestamp
        )

        val putRequest = PutItemRequest.builder()
                    .tableName(TABLE_SNAPSHOT)
                    .item(toDynamoAttributes(snapshot))
                    .build()

        val response = dynamoDb.putItem(putRequest)

        val dbCreatedTime = response?.attributes()?.get(COL_CREATED_TIME)?.n()?.toLongOrNull()

        return SaveSnapshotResponse(dbCreatedTime == snapshot.createdTime)
    }

	fun delete(productId: String) : String? {

		val request = DeleteItemRequest.builder()
            .returnValues(ReturnValue.ALL_OLD)
			.tableName(TABLE_SNAPSHOT)
            .key(mapOf(PRIMARY_KEY to strAttr(productId)))
            .build()

        val response = dynamoDb.deleteItem(request)
        // Return Product ID if available
		return response?.attributes()?.getOrDefault(PRIMARY_KEY, null)?.s()
	}

    private fun toAggregateSnapshotEntity(data : Map<String, AttributeValue>) : AggregateSnapshotEntity {

        // all of these columns should be present
        val productId     = data[PRIMARY_KEY]!!.s()
        val createdTime   = data[COL_CREATED_TIME]!!.n().toLong()
        val lastEventId   = data[COL_LATEST_EVENT_ID]!!.s()
        val lastEventTime = data[COL_LATEST_EVENT_TIME]!!.n().toLong()
        val csiJson       = data[COL_CSI]!!.s()

        val jsonNode  = JsonUtil.toJsonTree(csiJson)
        val clazz     = Class.forName(IceCreamStockItem::class.qualifiedName)
        val stockItem = JsonUtil.toObject(jsonNode, clazz) as IceCreamStockItem

        return AggregateSnapshotEntity(UUID.fromString(productId), stockItem, createdTime, lastEventId, lastEventTime)
    }

    private fun toDynamoAttributes(entity: AggregateSnapshotEntity) : Map<String,AttributeValue> {
        return hashMapOf(
                PRIMARY_KEY           to strAttr(entity.productId),
                COL_CSI               to strAttr(JsonUtil.toJson(entity.csi)),
                COL_CREATED_TIME      to numAttr(entity.createdTime),
                COL_LATEST_EVENT_ID   to strAttr(entity.latestEventId),
                COL_LATEST_EVENT_TIME to numAttr(entity.latestEventTime)
        )
    }
}
