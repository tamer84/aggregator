package com.tamer84.tango.product.aggregator.domain

import com.tamer84.tango.icecream.domain.IceCreamDomain
import com.tamer84.tango.icecream.domain.IceCreamDomain.*
import com.tamer84.tango.icecream.domain.IceCreamEvent
import com.tamer84.tango.icecream.domain.icsi.model.IceCreamStockItem
import com.tamer84.tango.product.aggregator.util.JsonUtil
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("TangoModelExtensions")

/**
 * Given json that represents a GenericEvent, deserialize into the appropriate GenericEvent sub-class
 *
 * @return GenericEvent sub-class or null if event class was not found
 */
fun deserializeEvent(eventJson : String) : IceCreamEvent? {

    val jsonNode     = JsonUtil.toJsonTree(eventJson)

    val payloadField = jsonNode["eventName"] ?: error("event is missing 'eventName' field so it cannot be deserialized")
    val clazzName    = payloadField.asText()

    return try {
        val clazz = Class.forName(clazzName)
        JsonUtil.toObject(jsonNode, clazz) as IceCreamEvent
    } catch(e : ClassNotFoundException) {
        log.warn("Event not recognized [$clazzName]")
        null
    }
}

fun IceCreamStockItem.merge(event : IceCreamEvent) {
    try {
        val onMethod = this.javaClass.getMethod("on", event.javaClass)
        onMethod.trySetAccessible()
        onMethod.invoke(this, event)
    }
    catch(e : NoSuchMethodException) {
        log.trace("Method not found [${e.message}]")
    }
}

fun IceCreamStockItem.getDomain(domain: IceCreamDomain) : Any {
    return when(domain) {
        AVAILABILITY -> this.availability
        MEDIA -> this.media
        PRICE -> this.pricing
        PRODUCT_RECORD -> this.productRecord
        else -> this.also {
            log.trace("Domain not mapped [domain=$domain]")
        }
    }
}
