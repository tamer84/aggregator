package com.tamer84.tango.product.aggregator.service


import com.nhaarman.mockitokotlin2.*
import com.tamer84.tango.icecream.domain.icsi.model.IceCreamStockItem
import com.tamer84.tango.product.aggregator.domain.AggregateSnapshotEntity
import com.tamer84.tango.product.aggregator.domain.SaveSnapshotResponse
import com.tamer84.tango.product.aggregator.repo.SnapshotMetadata
import com.tamer84.tango.product.aggregator.repo.SnapshotRepo
import com.tamer84.tango.product.aggregator.util.EnvVar
import org.junit.Test
import java.lang.System.currentTimeMillis
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotServiceTest {

    private val repo = mock<SnapshotRepo>()

    private val service = SnapshotService(repo)

    @Test
    fun testGetSnapshot() {

        val productId = UUID.randomUUID()
        val now   = currentTimeMillis()
        val entity = AggregateSnapshotEntity(productId, IceCreamStockItem(), now, "event123", now)

        whenever(repo.fetch(productId)).thenReturn(entity)

        val result = service.getSnapshot(productId)

        verify(repo).fetch(productId)
        assertEquals(entity, result)
    }

    @Test
    fun testSaveSnapshotMoreThanThreshold(){

        val stockItem = IceCreamStockItem()
        val metadata  = SnapshotMetadata("123", 1L, EnvVar.SNAPSHOT_EVENT_THRESHOLD)

        whenever(repo.save(stockItem, metadata)).thenReturn(SaveSnapshotResponse(true))

        val res = service.save(stockItem, metadata)

        verify(repo).save(stockItem, metadata)
        assertTrue { res }
    }

    @Test
    fun testSaveSnapshotLessThanThreshold(){

        val res = service.save(IceCreamStockItem(), SnapshotMetadata("123", 1L, EnvVar.SNAPSHOT_EVENT_THRESHOLD-1))

        verify(repo, never()).save(any(), any())
        assertFalse { res }
    }
}
