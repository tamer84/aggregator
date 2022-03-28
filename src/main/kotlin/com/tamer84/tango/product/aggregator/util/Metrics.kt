package com.tamer84.tango.product.aggregator.util

import com.codahale.metrics.*
import com.codahale.metrics.MetricRegistry.name
import com.codahale.metrics.json.MetricsModule
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import software.amazon.awssdk.core.interceptor.Context
import software.amazon.awssdk.core.interceptor.ExecutionAttribute
import software.amazon.awssdk.core.interceptor.ExecutionAttributes
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor
import java.util.concurrent.TimeUnit


object Metrics {

    private val metrics = MetricRegistry()

    private val objectMapper = jacksonObjectMapper()
            .registerModules(Jdk8Module())
            .registerModule(MetricsModule(TimeUnit.SECONDS, TimeUnit.MILLISECONDS, false, MetricFilter.ALL))
            .writerWithDefaultPrettyPrinter()

    fun createHistogram(group: String, name: String) : Histogram = metrics.histogram(name(group, name))

    fun createTimer(group: String, name: String) : Timer = metrics.timer(name(group, name))

    fun createCounter(group: String, name: String) : Counter = metrics.counter(name(group, name))

    fun metricsAsJson() : String {
        return objectMapper.writeValueAsString(metrics)
    }
}

class DynamoMetricInterceptor : ExecutionInterceptor {
    companion object {
        val timerAttribute : ExecutionAttribute<Timer.Context> = ExecutionAttribute("request-timer")
        val timer = Metrics.createTimer("dynamo", "request.duration")
    }

    override fun beforeExecution(context: Context.BeforeExecution?, executionAttributes: ExecutionAttributes?) {
        executionAttributes?.putAttribute(timerAttribute, timer.time())
    }
    override fun afterExecution(context: Context.AfterExecution?, executionAttributes: ExecutionAttributes?) {
        val timerCtx = executionAttributes?.getAttribute(timerAttribute) as Timer.Context
        timerCtx.stop()
    }
}

