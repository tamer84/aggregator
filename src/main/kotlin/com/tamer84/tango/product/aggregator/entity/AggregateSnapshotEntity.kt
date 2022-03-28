package com.tamer84.tango.product.aggregator.domain

import com.tamer84.tango.icecream.domain.icsi.model.IceCreamStockItem
import java.util.*


data class AggregateSnapshotEntity(val productId : UUID,
                                   val csi : IceCreamStockItem,
                                   val createdTime: Long,
                                   val latestEventId : String,
                                   val latestEventTime : Long)


data class SaveSnapshotResponse(val isSaved : Boolean, val message: String = "")
