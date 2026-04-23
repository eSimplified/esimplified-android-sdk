package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.OrdersRepository

import io.esimplified.sdk.model.ApiErrorResponse
import io.esimplified.sdk.model.OrderHistoryItem
import io.esimplified.sdk.model.OrderDetail
import io.esimplified.sdk.network.ApiService
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import timber.log.Timber

internal class OrdersRepositoryImpl(
    private val apiService: ApiService
) : OrdersRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // region Orders
    override suspend fun getOrderHistory(): List<OrderHistoryItem> {
        try {
            return apiService.getOrderHistory().results
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }

    override suspend fun getOrderHistory(withLoyaltyPoints: Boolean): List<OrderHistoryItem> {
        try {
            return apiService.getOrderHistory(usedPoints = if (withLoyaltyPoints) true else null).results
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }

    override suspend fun getOrderDetails(orderUuid: String): OrderDetail {
        try {
            return apiService.getOrderDetails(orderUuid, esimStatus = true, encodeQRCode = true)
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }

    override suspend fun trackOrder(orderUuid: String) {
        runCatching {
            apiService.getOrderStatus(orderUuid)
        }.onFailure {
            Timber.e(it)
        }
    }
    // endregion

    private fun parseHttpError(e: HttpException): String? {
        return try {
            val errorBody = e.response()?.errorBody()?.string()
            if (errorBody != null) {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(errorBody)
                errorResponse.detail ?: errorResponse.message ?: errorResponse.error
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
