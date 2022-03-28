package com.tamer84.tango.product.aggregator.domain

import com.tamer84.tango.icecream.domain.IceCreamEvent


data class AwsTangoEvent(val timestamp: Long, val eventId: String, val payload: IceCreamEvent)
