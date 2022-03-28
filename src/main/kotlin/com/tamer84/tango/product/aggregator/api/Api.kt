package com.tamer84.tango.product.aggregator.api

import com.jcabi.manifests.Manifests
import com.tamer84.tango.icecream.domain.IceCreamDomain
import com.tamer84.tango.icecream.domain.icsi.model.IceCreamStockItem
import com.tamer84.tango.product.aggregator.service.MetadataService
import com.tamer84.tango.product.aggregator.util.IocContainer.aggregateService
import com.tamer84.tango.product.aggregator.util.IocContainer.metadataService
import com.tamer84.tango.product.aggregator.util.IocContainer.snapshotService
import com.tamer84.tango.product.aggregator.util.Metrics
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.apibuilder.EndpointGroup
import io.javalin.plugin.openapi.OpenApiOptions
import io.javalin.plugin.openapi.annotations.HttpMethod
import io.javalin.plugin.openapi.dsl.OpenApiDocumentation
import io.javalin.plugin.openapi.dsl.document
import io.javalin.plugin.openapi.dsl.documented
import io.javalin.plugin.openapi.ui.SwaggerOptions
import io.swagger.v3.oas.models.info.Info
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import org.eclipse.jetty.http.HttpStatus.OK_200
import org.eclipse.jetty.http.HttpStatus.UNPROCESSABLE_ENTITY_422
import java.util.*


object ApiInfo {
    val title       = javaClass.`package`?.implementationTitle ?: "product-aggregator-app"
    val description = "Aggregates events into a single IceCreamStockItem"
    val version     = javaClass.`package`?.implementationVersion ?: "current"
    val gitHash     = kotlin.runCatching { Manifests.read("SCM-Revision") }.getOrDefault("(n/a)")
}

fun apiEndpoints() : EndpointGroup {

    return EndpointGroup {

        get("/") { ctx ->
            ctx.contentType("text/html").result("""
                <html>
                <h1>${ApiInfo.title.uppercase()}</h1>
                ${ApiInfo.description} (version: ${ApiInfo.version} - ${ApiInfo.gitHash})
                <ul>
                    <li><a href="/ping">Ping</a></li>
                    <li><a href="/metrics">Metrics</a></li>
                    <li><a href="/open-api">OpenAPI</a></li>
                    <li><a href="/swagger">Swagger</a></li>
                </ul>
                </html>
            """.trimIndent())
        }

        get("/ping") { ctx ->
            ctx.contentType("text/plain").result("pong")
        }

        get("/metrics") { ctx ->
            ctx.result(Metrics.metricsAsJson())
        }

        path("aggregator") {

            get(":id", documented(getAggregationWithIdDocumentation) { ctx ->
                val snapshot = ctx.queryParam("snapshot")?.toBoolean() ?: true
                val productId = UUID.fromString(ctx.pathParam("id"))
                val data = aggregateService.fetchAggregate(productId, snapshot)
                ctx.json(data)
            })

            get(":id/:domain", documented(getAggregationWithIdDocumentation) { ctx ->
                val snapshot = ctx.queryParam("snapshot")?.toBoolean() ?: true
                val productId = UUID.fromString(ctx.pathParam("id"))
                val domain = IceCreamDomain.valueOf(ctx.pathParam("domain"))
                val data = aggregateService.fetchAggregate(productId, domain, snapshot)
                ctx.json(data)
            })
        }

        path("snapshot") {
            delete(":id") {ctx ->
                val success = snapshotService.delete(ctx.pathParam("id"))
                ctx.status( if(success) OK_200 else UNPROCESSABLE_ENTITY_422 )
            }
        }

        path("metadata") {
            get(":sagaId", documented(getIngressTimeDocumentation) {
                ctx -> runBlocking(MDCContext()) {
                    val data = metadataService.getEventStats(ctx.pathParam("sagaId"))
                    ctx.json(data)
                }
            })
        }
    }
}

val getAggregationWithIdDocumentation: OpenApiDocumentation = document()
        .queryParam("snapshot", Boolean::class.java)
        .json("200", IceCreamStockItem::class.java)
val getIngressTimeDocumentation: OpenApiDocumentation = document()
    .json("200", MetadataService.EventStats::class.java)


fun getOpenApiOptions(): OpenApiOptions {
    val applicationInfo: Info = Info()
            .title(ApiInfo.title)
            .description("${ApiInfo.description} - [v${ApiInfo.version}]")
            .version(ApiInfo.version)
    return OpenApiOptions(applicationInfo)
            .path("/open-api")
            .ignorePath("/", HttpMethod.GET)
            .ignorePath("/favicon.ico", HttpMethod.GET)
            .swagger(SwaggerOptions("/swagger").title(ApiInfo.title))
}

