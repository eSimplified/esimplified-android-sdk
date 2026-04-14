package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.model.OrderHistoryItem
import io.esimplified.sdk.model.OrderDetail
import io.esimplified.sdk.network.ApiService
import timber.log.Timber

internal class OrdersRepositoryImpl(
    private val apiService: ApiService
) : OrdersRepository {

    // region Orders
    override suspend fun getOrderHistory(): List<OrderHistoryItem> {
        return apiService.getOrderHistory().results
    }

    override suspend fun getOrderHistory(withLoyaltyPoints: Boolean): List<OrderHistoryItem> {
        return apiService.getOrderHistory(usedPoints = if (withLoyaltyPoints) true else null).results
    }

    override suspend fun getOrderDetails(orderUuid: String): OrderDetail {
        return apiService.getOrderDetails(orderUuid, esimStatus = true, encodeQRCode = true)
    }

    override suspend fun trackOrder(orderUuid: String) {
        runCatching {
            apiService.getOrderStatus(orderUuid)
        }.onFailure {
            Timber.e(it)
        }
    }
    // endregion
}
