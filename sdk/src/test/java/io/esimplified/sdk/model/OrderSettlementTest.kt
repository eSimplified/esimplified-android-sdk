package io.esimplified.sdk.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderSettlementTest {

    @Test
    fun `terminal payment status is a failure regardless of order status`() {
        assertTrue(SettlementRules.isFailure("pending", "failed"))
    }

    @Test
    fun `terminal order status is a failure when payment status is absent`() {
        assertTrue(SettlementRules.isFailure("cancelled", null))
    }

    @Test
    fun `padded and upper case statuses still match`() {
        assertTrue(SettlementRules.isFailure(" Cancelled ", null))
        assertTrue(SettlementRules.isFailure("pending", " DECLINED"))
    }

    @Test
    fun `canceled with one l and expired are terminal`() {
        assertTrue(SettlementRules.isFailure("canceled", null))
        assertTrue(SettlementRules.isFailure("expired", null))
    }

    @Test
    fun `pending is not a failure`() {
        assertFalse(SettlementRules.isFailure("pending", "pending"))
    }

    @Test
    fun `fulfilled needs an iccid and a success payment status`() {
        assertTrue(SettlementRules.isFulfilled("success", "success", "8901"))
        assertFalse(SettlementRules.isFulfilled("success", "success", ""))
        assertFalse(SettlementRules.isFulfilled("success", "success", null))
        assertFalse(SettlementRules.isFulfilled("success", "pending", "8901"))
    }

    @Test
    fun `a terminal order status is never fulfilled even with a success payment status`() {
        assertFalse(SettlementRules.isFulfilled("failed", "success", "8901"))
        assertFalse(SettlementRules.isFulfilled("cancelled", "success", "8901"))
        assertFalse(SettlementRules.isFulfilled("success", "declined", "8901"))
    }

    @Test
    fun `blank payment status falls back to order status`() {
        assertTrue(SettlementRules.isFulfilled("success", null, "8901"))
        assertTrue(SettlementRules.isFulfilled("success", "  ", "8901"))
        assertFalse(SettlementRules.isFulfilled("pending", null, "8901"))
    }

    @Test
    fun `probe decodes with only order status present`() {
        val probe = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }.decodeFromString(OrderStatusProbe.serializer(), """{"order_status":"pending","order_number":1}""")
        assertEquals("pending", probe.orderStatus)
        assertNull(probe.paymentStatus)
        assertNull(probe.iccid)
    }
}
