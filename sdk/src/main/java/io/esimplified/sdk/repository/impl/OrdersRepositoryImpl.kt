package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.OrdersRepository

import io.esimplified.sdk.model.OrderHistoryItem
import io.esimplified.sdk.model.OrderDetail
import io.esimplified.sdk.model.OrdersPage
import io.esimplified.sdk.network.ApiErrorMessage
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.RepositoryResult
import io.esimplified.sdk.repository.cachedListResult
import io.esimplified.sdk.repository.cachedResult
import io.esimplified.sdk.repository.listOrThrow
import io.esimplified.sdk.repository.valueOrThrow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import retrofit2.HttpException
import io.esimplified.sdk.SdkLog

internal class OrdersRepositoryImpl(
    private val apiService: ApiService,
    private val cache: SdkCache,
) : OrdersRepository {

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
                apiService.getOrderHistory(limit = UNPAGED_LIMIT).results
            } catch (e: HttpException) {
                throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
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
                apiService.getOrderHistory(
                    usedPoints = if (withLoyaltyPoints) true else null,
                    limit = UNPAGED_LIMIT,
                ).results
            } catch (e: HttpException) {
                throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
            }
        }

    override suspend fun getOrdersPageResult(
        limit: Int,
        offset: Int,
        withLoyaltyPoints: Boolean,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<OrdersPage> {
        val result = cache.cachedResult<OrdersPage>(
            ordersPageKey(withLoyaltyPoints, limit, offset),
            forceRefresh,
            cacheTTL,
        ) {
            try {
                val response = apiService.getOrderHistory(
                    usedPoints = if (withLoyaltyPoints) true else null,
                    limit = limit,
                    offset = offset,
                )
                OrdersPage(
                    orders = response.results,
                    totalCount = response.count,
                    hasMore = !response.next.isNullOrEmpty(),
                )
            } catch (e: HttpException) {
                throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
            }
        }
        return RepositoryResult(
            value = result.value ?: OrdersPage(),
            isStale = result.isStale,
            failure = result.failure,
        )
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
    ): RepositoryResult<OrderDetail?> {
        val key = orderKey(orderUuid)
        if (!forceRefresh) {
            val cached = cache.get<OrderDetail>(key)
            if (cached != null) return RepositoryResult(cached)
        }
        var attempt = 1
        while (true) {
            val result = cache.cachedResult<OrderDetail>(key, forceRefresh = true, cacheTTL) {
                try {
                    apiService.getOrderDetails(orderUuid, esimStatus = true, encodeQRCode = true)
                } catch (e: HttpException) {
                    throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
                }
            }
            val order = result.value
            if (result.didFail || order == null || order.orderStatus != PENDING_ORDER_STATUS) {
                return result
            }
            cache.remove(key)
            if (attempt >= PENDING_ORDER_ATTEMPTS) {
                SdkLog.w("An order is still pending after $attempt attempts")
                return RepositoryResult(order)
            }
            attempt++
            delay(PENDING_ORDER_RETRY_DELAY)
        }
    }

    override suspend fun getOrderInvoice(orderUuid: String): ByteArray =
        try {
            apiService.getOrderInvoice(orderUuid).bytes()
        } catch (e: HttpException) {
            throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
        }

    override suspend fun trackOrder(orderUuid: String) {
        runCatching {
            apiService.getOrderStatus(orderUuid)
        }.onFailure {
            SdkLog.e("Order conversion tracking failed", it)
        }
    }
    // endregion

    // region Cache
    override suspend fun invalidateCache() {
        cache.removeWithPrefix(ORDERS_KEY_PREFIX)
        cache.removeWithPrefix(ORDER_KEY_PREFIX)
    }

    private fun orderKey(orderUuid: String): String = "$ORDER_KEY_PREFIX$orderUuid"

    private fun ordersPageKey(withLoyaltyPoints: Boolean, limit: Int, offset: Int): String =
        "$ORDERS_PAGE_KEY_PREFIX${withLoyaltyPoints}_${limit}_$offset"
    // endregion

    private companion object {
        const val UNPAGED_LIMIT = 500
        const val ORDERS_KEY_PREFIX = "orders_"
        const val ORDERS_PAGE_KEY_PREFIX = "orders_page_"
        const val ORDER_KEY_PREFIX = "order_"
        const val PENDING_ORDER_STATUS = "pending"
        const val PENDING_ORDER_ATTEMPTS = 5
        val PENDING_ORDER_RETRY_DELAY = 1.seconds
    }
}
