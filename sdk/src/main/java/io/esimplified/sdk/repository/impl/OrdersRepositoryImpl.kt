package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.OrdersRepository

import io.esimplified.sdk.model.ApiErrorResponse
import io.esimplified.sdk.model.OrderHistoryItem
import io.esimplified.sdk.model.OrderDetail
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.RepositoryResult
import io.esimplified.sdk.repository.cachedListResult
import io.esimplified.sdk.repository.cachedResult
import io.esimplified.sdk.repository.listOrThrow
import io.esimplified.sdk.repository.valueOrThrow
import kotlin.time.Duration
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import timber.log.Timber

internal class OrdersRepositoryImpl(
    private val apiService: ApiService,
    private val cache: SdkCache,
) : OrdersRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // region Orders
    override suspend fun getOrderHistory(
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): List<OrderHistoryItem> = getOrderHistoryResult(
        forceRefresh = forceRefresh,
        cacheTTL = cacheTTL,
    ).listOrThrow()

    override suspend fun getOrderHistoryResult(
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<List<OrderHistoryItem>> =
        cache.cachedListResult("${ORDERS_KEY_PREFIX}none", forceRefresh, cacheTTL) {
            try {
                apiService.getOrderHistory().results
            } catch (e: HttpException) {
                throw Exception(parseHttpError(e) ?: e.message)
            }
        }

    override suspend fun getOrderHistory(
        withLoyaltyPoints: Boolean,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): List<OrderHistoryItem> = getOrderHistoryResult(
        withLoyaltyPoints = withLoyaltyPoints,
        forceRefresh = forceRefresh,
        cacheTTL = cacheTTL,
    ).listOrThrow()

    override suspend fun getOrderHistoryResult(
        withLoyaltyPoints: Boolean,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<List<OrderHistoryItem>> =
        cache.cachedListResult("$ORDERS_KEY_PREFIX$withLoyaltyPoints", forceRefresh, cacheTTL) {
            try {
                apiService.getOrderHistory(usedPoints = if (withLoyaltyPoints) true else null).results
            } catch (e: HttpException) {
                throw Exception(parseHttpError(e) ?: e.message)
            }
        }

    override suspend fun getOrderDetails(
        orderUuid: String,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): OrderDetail = getOrderDetailsResult(orderUuid, forceRefresh, cacheTTL).valueOrThrow()

    override suspend fun getOrderDetailsResult(
        orderUuid: String,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<OrderDetail?> =
        cache.cachedResult<OrderDetail>(orderKey(orderUuid), forceRefresh, cacheTTL) {
            try {
                apiService.getOrderDetails(orderUuid, esimStatus = true, encodeQRCode = true)
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

    // region Cache
    override suspend fun invalidateCache() {
        cache.removeWithPrefix(ORDERS_KEY_PREFIX)
        cache.removeWithPrefix(ORDER_KEY_PREFIX)
    }

    private fun orderKey(orderUuid: String): String = "$ORDER_KEY_PREFIX$orderUuid"
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

    private companion object {
        const val ORDERS_KEY_PREFIX = "orders_"
        const val ORDER_KEY_PREFIX = "order_"
    }
}
