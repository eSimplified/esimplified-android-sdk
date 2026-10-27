package io.esimplified.sdk.repository

import io.esimplified.sdk.model.OrderDetail
import io.esimplified.sdk.model.OrderHistoryItem
import io.esimplified.sdk.model.OrderSettlement
import io.esimplified.sdk.model.OrdersPage
import io.esimplified.sdk.network.SdkError
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
    fun `a zero timeout with a pending order returns pending after one read`() = runTest {
        val orders = ScriptedOrders(listOf(ok(order("pending"))))

        val result = orders.awaitSettlement("uuid-1", interval = 1.seconds, timeout = Duration.ZERO)

        assertEquals(OrderSettlement.Pending, result)
        assertEquals(1, orders.calls)
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
