package com.tamer84.tango.product.aggregator.service

import com.tamer84.tango.icecream.domain.icsi.model.IceCreamStockItem
import com.tamer84.tango.product.aggregator.domain.AggregateSnapshotEntity
import com.tamer84.tango.product.aggregator.repo.SnapshotMetadata
import com.tamer84.tango.product.aggregator.repo.SnapshotRepo
import com.tamer84.tango.product.aggregator.util.EnvVar

import org.slf4j.LoggerFactory
import java.util.*

class SnapshotService (private val repo : SnapshotRepo,
                       private val numberOfEventsThreshold : Int = EnvVar.SNAPSHOT_EVENT_THRESHOLD) {

    companion object {
        private val log = LoggerFactory.getLogger(SnapshotService::class.java)
    }

    fun getSnapshot(productId : UUID) : AggregateSnapshotEntity? {
        return repo.fetch(productId)
    }

    fun save(stockItem: IceCreamStockItem, metadata: SnapshotMetadata) : Boolean {

        log.debug("Saving snapshot [productId=${stockItem.id}]")

        return when(metadata.numOfNewEvents < numberOfEventsThreshold) {

            true -> false.also {
                log.warn("NOT Saving snapshot. Total new events has not reached the event threshold [events={}, threshold={}]",
                        metadata.numOfNewEvents,
                        numberOfEventsThreshold
                )
            }
            else -> {
                val result = repo.save(stockItem, metadata)
                if(! result.isSaved)
                    log.warn("Snapshot NOT saved [productId=${stockItem.id}, metadata=$metadata, msg=${result.message}]")
                return result.isSaved
            }
        }
    }

	fun delete(productId : String) : Boolean {

        log.info("Deleting snapshot [productId=$productId]")

        return runCatching {
            repo.delete(productId) == productId
        }.onSuccess {result ->
            log.info("Deleted snapshot [productId=$productId, result=$result]")
        }.onFailure {
            log.warn("Error deleting snapshot [productId=$productId, result=$it]")
        }.getOrDefault(false)
	}
}
