package com.tamer84.tango.product.aggregator.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

import java.io.InputStream


private val mapper: ObjectMapper = jacksonObjectMapper()
        .registerModules(Jdk8Module())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

object JsonUtil {

    fun toJsonTree(json: String) : JsonNode = mapper.readTree(json)

    fun <T> toObject(node: JsonNode, clazz: Class<T>) : T = mapper.treeToValue(node, clazz)

    fun <T> toObject(node: InputStream, clazz: Class<T>) : T = mapper.readValue(node, clazz)

    fun toJson(any: Any) : String = mapper.writeValueAsString(any);

}
