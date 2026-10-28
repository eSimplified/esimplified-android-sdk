package io.esimplified.sdk.repository

import io.esimplified.sdk.model.OrderDetail
import io.esimplified.sdk.model.OrderHistoryItem
import io.esimplified.sdk.model.OrderSettlement
import io.esimplified.sdk.model.OrdersPage
import io.esimplified.sdk.network.SdkError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class OrdersRepositoryDefaultSettlementTest {

    private class ScriptedOrders(private val script: List<RepositoryResult<OrderDetail?>>) : OrdersRepository {
        var calls = 0
            private set

        override suspend fun getOrderDetailsResult(
            orderUuid: String,
            forceRefresh: Boolean,
            cacheTTL: Duration,
        ): RepositoryResult<OrderDetail?> = script[minOf(calls++, script.lastIndex)]

        override suspend fun getOrderHistory(
            withLoyaltyPoints: Boolean,
            forceRefresh: Boolean,
            cacheTTL: Duration,
        ): List<OrderHistoryItem> = emptyList()

        override suspend fun getOrderDetails(
            orderUuid: String,
            forceRefresh: Boolean,
            cacheTTL: Duration,
        ): OrderDetail = throw UnsupportedOperationException()

        override suspend fun getOrdersPageResult(
            limit: Int,
            offset: Int,
            withLoyaltyPoints: Boolean,
            forceRefresh: Boolean,
            cacheTTL: Duration,
        ): RepositoryResult<OrdersPage> = RepositoryResult(OrdersPage())

        override suspend fun trackOrder(orderUuid: String) = Unit
    }

    private class DetailsOnlyOrders(private val onCall: (Int) -> OrderDetail) : OrdersRepository {
        var calls = 0
            private set

        override suspend fun getOrderDetails(
            orderUuid: String,
            forceRefresh: Boolean,
            cacheTTL: Duration,
        ): OrderDetail = onCall(calls++)

        override suspend fun getOrderHistory(
            withLoyaltyPoints: Boolean,
            forceRefresh: Boolean,
            cacheTTL: Duration,
        ): List<OrderHistoryItem> = emptyList()

        override suspend fun getOrdersPageResult(
            limit: Int,
            offset: Int,
            withLoyaltyPoints: Boolean,
            forceRefresh: Boolean,
            cacheTTL: Duration,
        ): RepositoryResult<OrdersPage> = RepositoryResult(OrdersPage())

        override suspend fun trackOrder(orderUuid: String) = Unit
    }

    private class HangingOrders : OrdersRepository {
        override suspend fun getOrderDetailsResult(
            orderUuid: String,
            forceRefresh: Boolean,
            cacheTTL: Duration,
        ): RepositoryResult<OrderDetail?> = awaitCancellation()

        override suspend fun getOrderHistory(
            withLoyaltyPoints: Boolean,
            forceRefresh: Boolean,
            cacheTTL: Duration,
        ): List<OrderHistoryItem> = emptyList()

        override suspend fun getOrderDetails(
            orderUuid: String,
            forceRefresh: Boolean,
            cacheTTL: Duration,
        ): OrderDetail = throw UnsupportedOperationException()

        override suspend fun getOrdersPageResult(
            limit: Int,
            offset: Int,
            withLoyaltyPoints: Boolean,
            forceRefresh: Boolean,
            cacheTTL: Duration,
        ): RepositoryResult<OrdersPage> = RepositoryResult(OrdersPage())

        override suspend fun trackOrder(orderUuid: String) = Unit
    }

    @Test
    fun `a read that never returns is bounded and the wait ends pending`() = runBlocking {
        val orders = HangingOrders()

        val elapsed = measureTime {
            assertEquals(
                OrderSettlement.Pending,
                orders.awaitSettlement("uuid-1", interval = 50.milliseconds, timeout = 100.milliseconds),
            )
        }

        assertTrue("took $elapsed", elapsed < 2.seconds)
    }

    @Test
    fun `pending then fulfilled returns fulfilled after two reads`() = runTest {
        val orders = ScriptedOrders(listOf(ok(order("pending")), ok(order("success", iccid = "8901"))))

        val result = orders.awaitSettlement("uuid-1")

        assertTrue(result is OrderSettlement.Fulfilled)
        assertEquals(2, orders.calls)
    }

    @Test
    fun `a failed order stops after one read`() = runTest {
        val orders = ScriptedOrders(listOf(ok(order("cancelled"))))

        val result = orders.awaitSettlement("uuid-1")

        assertTrue(result is OrderSettlement.Failed)
        assertEquals(1, orders.calls)
    }

    @Test
    fun `a failed read is ignored and polling continues`() = runTest {
        val orders = ScriptedOrders(
            listOf(
                RepositoryResult<OrderDetail?>(null, failure = SdkError.NetworkError(500, "boom")),
                ok(order("success", iccid = "8901")),
            )
        )

        val result = orders.awaitSettlement("uuid-1")

        assertTrue(result is OrderSettlement.Fulfilled)
        assertEquals(2, orders.calls)
    }

    @Test
    fun `a failed read carrying a stale fulfilled value is ignored`() = runTest {
        val orders = ScriptedOrders(
            listOf(
                RepositoryResult<OrderDetail?>(
                    order("success", iccid = "8901"),
                    isStale = true,
                    failure = SdkError.NetworkError(500, "boom"),
                ),
                ok(order("success", iccid = "8902")),
            )
        )

        val result = orders.awaitSettlement("uuid-1") as OrderSettlement.Fulfilled

        assertEquals("8902", result.order.iccid)
        assertEquals(2, orders.calls)
    }

    @Test
    fun `a zero timeout with a pending order returns pending after one read`() = runTest {
        val orders = ScriptedOrders(listOf(ok(order("pending"))))

        val result = orders.awaitSettlement("uuid-1", interval = 1.seconds, timeout = Duration.ZERO)

        assertEquals(OrderSettlement.Pending, result)
        assertEquals(1, orders.calls)
    }

    @Test
    fun `a read that throws is ignored and polling continues`() = runTest {
        val orders = DetailsOnlyOrders { call ->
            if (call == 0) throw RuntimeException("boom") else order("success", iccid = "8901")
        }

        val result = orders.awaitSettlement("uuid-1")

        assertTrue(result is OrderSettlement.Fulfilled)
        assertEquals(2, orders.calls)
    }

    @Test
    fun `a read that throws cancellation propagates`() = runTest {
        val orders = DetailsOnlyOrders { throw CancellationException("cancelled") }

        assertThrows(CancellationException::class.java) {
            runBlocking { orders.awaitSettlement("uuid-1") }
        }
        assertEquals(1, orders.calls)
    }

    @Test
    fun `a zero interval is rejected`() = runTest {
        val orders = ScriptedOrders(listOf(ok(order("pending"))))

        try {
            orders.awaitSettlement("uuid-1", interval = Duration.ZERO)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertEquals(0, orders.calls)
        }
    }

    private fun ok(order: OrderDetail): RepositoryResult<OrderDetail?> = RepositoryResult(order)

    private fun order(status: String, iccid: String? = null): OrderDetail {
        val iccidField = if (iccid == null) "" else "\"iccid\": \"$iccid\","
        return Json { ignoreUnknownKeys = true; coerceInputValues = true }.decodeFromString(
            """
            {
                $iccidField
                "customer_id": "cust-1",
                "discount_amount": 0.0,
                "discount_code": "",
                "final_price": 9.99,
                "order_date": "2026-01-01",
                "order_number": 1001,
                "order_status": "$status",
                "order_type": "BUY",
                "package_data_size": 1.0,
                "package_type_id": 42,
                "package_name": "1 GB / 7 Days",
                "package_validity": 7,
                "purchase_currency": "USD",
                "purchase_currency_obj": {"symbol": "$", "iso": "USD"},
                "purchase_price": 9.99,
                "payment_method": "stripe_intent"
            }
            """.trimIndent()
        )
    }
}
