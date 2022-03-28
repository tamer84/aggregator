package com.tamer84.tango.product.aggregator.repo

import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.util.*

object DynamoUtil {

    fun strAttr(uuid: UUID) : AttributeValue = strAttr(uuid.toString())

    fun strAttr(str : String): AttributeValue = AttributeValue.builder().s(str).build()

    fun numAttr(long: Long): AttributeValue = AttributeValue.builder().n(long.toString()).build()

}
