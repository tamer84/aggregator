package com.tamer84.tango.product.aggregator.api

import com.tamer84.tango.product.aggregator.util.Metrics
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.ExceptionHandler
import org.slf4j.LoggerFactory
import org.slf4j.MDC


private val log = LoggerFactory.getLogger("ExceptionMapper")

data class ApiError(val status: Int = 500,
                    val message: String? = "Request failed",
                    val traceId : String? = "")

class NotFoundException(message: String) : Exception(message)

inline fun notFoundIf(value: Boolean, lazyMessage: () -> Any) {
    if (value) {
        val message = lazyMessage()
        throw NotFoundException(message.toString())
    }
}

fun mapExceptions(app: Javalin) {

    app.exception(IllegalAccessException::class.java, ApiExceptionHandler(status = 401))
    app.exception(IllegalArgumentException::class.java, ApiExceptionHandler(status = 400))
    app.exception(IllegalStateException::class.java, ApiExceptionHandler(status = 500))
    app.exception(NotFoundException::class.java, ApiExceptionHandler(status = 404))
    app.exception(NullPointerException::class.java, ApiExceptionHandler(status = 400))
    app.exception(Exception::class.java, ApiExceptionHandler(status = 500))

}

class ApiExceptionHandler(val status:Int = 500) : ExceptionHandler<Exception> {

    companion object {
        private val errorCounter = Metrics.createCounter("aggregate", "error.total")
        private val notFoundErrorCounter = Metrics.createCounter("aggregate", "error.notfound.total")
        private val userErrorCounter = Metrics.createCounter("aggregate", "error.user.total")
    }

    override fun handle(e: Exception, ctx: Context) {

        when (e) {
            is java.lang.IllegalArgumentException -> userErrorCounter.inc()
            is NotFoundException -> notFoundErrorCounter.inc()
            else -> errorCounter.inc()
        }

        log.error("Request failed", e)
        val traceId = kotlin.runCatching { MDC.get("traceId") }
        ctx.status(status).json(ApiError(status, e.message, traceId.getOrDefault("not_available")))
    }

}
