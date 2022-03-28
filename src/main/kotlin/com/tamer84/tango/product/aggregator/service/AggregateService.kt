package com.tamer84.tango.product.aggregator.service


import com.tamer84.tango.icecream.domain.IceCreamDomain
import com.tamer84.tango.icecream.domain.icsi.model.IceCreamStockItem
import com.tamer84.tango.product.aggregator.api.NotFoundException
import com.tamer84.tango.product.aggregator.api.notFoundIf
import com.tamer84.tango.product.aggregator.domain.AwsTangoEvent
import com.tamer84.tango.product.aggregator.domain.getDomain
import com.tamer84.tango.product.aggregator.domain.merge
import com.tamer84.tango.product.aggregator.repo.EventRepo
import com.tamer84.tango.product.aggregator.repo.SnapshotMetadata
import com.tamer84.tango.product.aggregator.util.EnvVar
import com.tamer84.tango.product.aggregator.util.Metrics
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.*
import kotlin.system.measureTimeMillis

class AggregateService(private val repo : EventRepo, private val snapshotService: SnapshotService) {

    companion object {
        private val log = LoggerFactory.getLogger(AggregateService::class.java)
        private val eventTotalHistogram = Metrics.createHistogram("aggregate", "events.total")
        private val eventTotalSnapshotHistogram = Metrics.createHistogram("aggregate", "events.total.snapshot")
    }

    init {
        if(EnvVar.SNAPSHOT_DISABLE_UPDATES) {
            log.warn("Aggregate Update is DISABLED -- No new event will be applied to the existing aggregate (snapshot)")
        }
    }


    fun fetchAggregate(productId: UUID, useSnapshots : Boolean) : IceCreamStockItem {
        MDC.put("productId", productId.toString()) // make available for subsequent logs
        val useOnlySnapshots = useSnapshots && EnvVar.SNAPSHOT_DISABLE_UPDATES
        return when {
            useOnlySnapshots -> buildSnapshotOnlyAggregate(productId)
            useSnapshots     -> buildSnapshotAggregate(productId)
            else             -> buildAggregate(productId)
        }
    }

    fun fetchAggregate(productId: UUID, domain: IceCreamDomain, useSnapshots : Boolean) : Any {

        val stockItem = fetchAggregate(productId, useSnapshots)

        return stockItem.getDomain(domain)
    }

    /**
     * Builds aggregate from scratch using all events in the events table
     */
    private fun buildAggregate(productId : UUID) : IceCreamStockItem {

        log.warn("AGGREGATE_ONLY - Not using aggregate snapshots -- Significant degradation of performance is expected!!")

        val events = repo.fetchEventsByProductId(productId)
        notFoundIf(events.isEmpty()) { "ProductStockItem not found [productId=$productId]" }

        eventTotalHistogram.update(events.size)

        return apply(events, IceCreamStockItem(productId.toString()))
    }

    /**
     * Builds the aggregate using only the snapshot.  No new events are applied!!!
     */
    private fun buildSnapshotOnlyAggregate(productId: UUID) : IceCreamStockItem {
        log.warn("SNAPSHOT_ONLY - Aggregate Update is DISABLED -- No new events will be applied to the existing aggregate (snapshot)")
        val snapshot = snapshotService.getSnapshot(productId)
        if(snapshot?.csi == null)
            throw NotFoundException("ConnectStockItem snapshot not found and Updates are NOT allowed [productId=$productId]")
        return snapshot.csi
    }

    /**
     * Builds the aggregate using first the snapshot and then applying all events since the last snapshot.
     *
     * If snapshot does not exist, then aggregate is built from all events and a new snapshot is saved
     */
    private fun buildSnapshotAggregate(productId: UUID) : IceCreamStockItem {

        log.info("SNAPSHOT - Building aggregate from snapshot")

        // 1 - GET Snapshot
        val snapshot = runCatching { snapshotService.getSnapshot(productId) }
                      .onFailure { log.warn("Error getting snapshot, building aggregate without snapshot", it) }
                      .getOrNull()


        // 2 - GET Events (not found in Snapshot)
        val bufferSeconds = EnvVar.SNAPSHOT_EVENT_TIME_BUFFER * 1000
        val timestamp = snapshot?.latestEventTime?.minus(bufferSeconds)

        val events = repo.fetchEventsByProductId(productId, timestamp)
        notFoundIf(events.isEmpty() && snapshot?.csi == null) { "ConnectStockItem not found [productId=$productId]" }
        eventTotalSnapshotHistogram.update(events.size)

        // 3 - CREATE Aggregate

        // Take the snapshot as base or an empty VSI
        val baseVsi: IceCreamStockItem = snapshot?.csi ?: IceCreamStockItem(productId.toString())

        val aggregatedVsi = apply(events, baseVsi)

        // 4 - SAVE New Snapshot
        GlobalScope.launch {
            val latestEvent = events.maxByOrNull { it.timestamp } ?: error("Failed to get latest event")
            snapshotService.save(
                    aggregatedVsi,
                    SnapshotMetadata(latestEvent.eventId, latestEvent.timestamp, events.size)
            )
        }

        return aggregatedVsi
    }


    /**
     * Builds the ConnectStockItem by applying all of the given events
     */
    private fun apply(events: List<AwsTangoEvent>, stockItem: IceCreamStockItem): IceCreamStockItem {
        val duration = measureTimeMillis {
            events.forEach { stockItem.merge(it.payload) }
        }

        log.info("Aggregation complete [durationMs=$duration, totalAddedEvents=${events.size}]")

        return stockItem
    }

}
