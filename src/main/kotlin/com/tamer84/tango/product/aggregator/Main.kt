package com.tamer84.tango.product.aggregator


import com.tamer84.tango.product.aggregator.api.ApiInfo
import com.tamer84.tango.product.aggregator.api.apiEndpoints
import com.tamer84.tango.product.aggregator.api.getOpenApiOptions
import com.tamer84.tango.product.aggregator.api.mapExceptions
import com.tamer84.tango.product.aggregator.util.EnvVar
import io.javalin.Javalin
import io.javalin.core.compression.CompressionStrategy.Companion.GZIP
import io.javalin.core.util.Header
import io.javalin.plugin.openapi.OpenApiPlugin
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.*

private val log = LoggerFactory.getLogger("Main")

fun main() {

    val app = Javalin.create { config ->
        config.compressionStrategy(GZIP)
        config.defaultContentType = "application/json"
        config.registerPlugin(OpenApiPlugin(getOpenApiOptions()))
        config.requestLogger { ctx, ms ->
            ctx.header(Header.SERVER, ApiInfo.title)
            ctx.header("traceId", MDC.get("traceId"))
            MDC.put("status", ctx.status().toString())
            MDC.put("durationMs", ms.toInt().toString())
            if("/ping" != ctx.path())
                log.info("REQUEST Completed")
            MDC.clear()
        }
        config.showJavalinBanner = false

    }.start(EnvVar.PORT)

    app.get("/favicon.ico") { ctx -> ctx.status(204)}

    // Setup MDCs
    app.before { ctx ->
        MDC.put("traceId", ctx.header("traceId") ?: UUID.randomUUID().toString())
        MDC.put("method", ctx.method())
        MDC.put("path", ctx.path())

        if("/ping" != ctx.path())
            log.info("REQUEST Started")
    }

    mapExceptions(app)

    app.routes(apiEndpoints())

}
