package com.tamer84.tango.product.aggregator.service

import com.tamer84.tango.product.aggregator.repo.EventRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

class MetadataService(private val repo : EventRepo) {
    companion object {
        private val log = LoggerFactory.getLogger(MetadataService::class.java)
    }

    suspend fun getEventStats(sagaId : String) : EventStats = coroutineScope {
        val ingressTime = async(Dispatchers.IO) { repo.fetchIngressTime(sagaId) }
        val eventCount = async(Dispatchers.IO) { repo.fetchEventCount(sagaId) }

        EventStats(sagaId= sagaId, ingressTime =  ingressTime.await(), eventCount = eventCount.await())
    }

    data class EventStats(val sagaId: String, val ingressTime : Long?, val eventCount : Int? = null)
}
