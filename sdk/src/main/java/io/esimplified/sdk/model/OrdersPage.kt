package io.esimplified.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class OrdersPage(
    val orders: List<OrderHistoryItem> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
)
