package com.tamer84.tango.product.aggregator.api

import org.junit.Test

class ExceptionMapperTest {

    @Test(expected = NotFoundException::class)
    fun testNotFoundIfTrue() {
        notFoundIf(true) { "WTF" }
    }

    @Test
    fun testNotFoundIfFalse() {
        notFoundIf(false) { "WTF" }
    }
}
