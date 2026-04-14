package io.esimplified.sdk.repository

import io.esimplified.sdk.model.OrderHistoryItem
import io.esimplified.sdk.model.OrderDetail

interface OrdersRepository {
    suspend fun getOrderHistory(): List<OrderHistoryItem>
    suspend fun getOrderHistory(withLoyaltyPoints: Boolean): List<OrderHistoryItem>
    suspend fun getOrderDetails(orderUuid: String): OrderDetail
    suspend fun trackOrder(orderUuid: String)
}
