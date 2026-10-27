package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class OrderSettlement {
    data class Fulfilled(val order: OrderDetail) : OrderSettlement()
    data class Failed(val order: OrderDetail?) : OrderSettlement()
    data object Pending : OrderSettlement()
}

val OrderDetail.isSettlementFailure: Boolean
    get() = SettlementRules.isFailure(orderStatus, paymentStatus)

val OrderDetail.isFulfilled: Boolean
    get() = SettlementRules.isFulfilled(orderStatus, paymentStatus, iccid)

@Serializable
internal data class OrderStatusProbe(
    @SerialName("order_status") val orderStatus: String = "",
    @SerialName("payment_status") val paymentStatus: String? = null,
    @SerialName("iccid") val iccid: String? = null,
) {
    val isSettlementFailure: Boolean
        get() = SettlementRules.isFailure(orderStatus, paymentStatus)

    val isFulfilled: Boolean
        get() = SettlementRules.isFulfilled(orderStatus, paymentStatus, iccid)
}

internal object SettlementRules {
    private val terminalStatuses = setOf("failed", "cancelled", "canceled", "declined", "expired")

    private fun normalise(status: String?): String? =
        status?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    fun isFailure(orderStatus: String, paymentStatus: String?): Boolean =
        normalise(paymentStatus) in terminalStatuses || normalise(orderStatus) in terminalStatuses

    fun isFulfilled(orderStatus: String, paymentStatus: String?, iccid: String?): Boolean {
        if (iccid.isNullOrBlank()) return false
        val status = normalise(paymentStatus) ?: normalise(orderStatus)
        return status == "success"
    }
}
