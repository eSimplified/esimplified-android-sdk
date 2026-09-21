package io.esimplified.sdk.repository

import io.esimplified.sdk.model.OrderHistoryItem
import io.esimplified.sdk.model.OrderDetail
import io.esimplified.sdk.model.OrdersPage
import io.esimplified.sdk.network.SdkError
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface OrdersRepository {

    // region Reads
    suspend fun getOrderHistory(
        withLoyaltyPoints: Boolean = false,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = ORDERS_LIST_TTL,
    ): List<OrderHistoryItem>

    suspend fun getOrderDetails(
        orderUuid: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = ORDER_DETAIL_TTL,
    ): OrderDetail
    // endregion

    // region Result reads
    suspend fun getOrderHistoryResult(
        withLoyaltyPoints: Boolean = false,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = ORDERS_LIST_TTL,
    ): RepositoryResult<List<OrderHistoryItem>> =
        RepositoryResult(
            getOrderHistory(
                withLoyaltyPoints = withLoyaltyPoints,
                forceRefresh = forceRefresh,
                cacheTTL = cacheTTL,
            )
        )

    suspend fun getOrderDetailsResult(
        orderUuid: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = ORDER_DETAIL_TTL,
    ): RepositoryResult<OrderDetail?> =
        RepositoryResult(getOrderDetails(orderUuid, forceRefresh, cacheTTL))

    suspend fun getOrdersPageResult(
        limit: Int = ORDERS_PAGE_LIMIT,
        offset: Int = 0,
        withLoyaltyPoints: Boolean = false,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = ORDERS_LIST_TTL,
    ): RepositoryResult<OrdersPage>
    // endregion

    // region Invoice
    suspend fun getOrderInvoice(orderUuid: String): ByteArray =
        throw SdkError.NetworkError(501, "Invoice download is not implemented by this repository")
    // endregion

    // region Writes
    suspend fun trackOrder(orderUuid: String)
    // endregion

    // region Cache
    suspend fun invalidateCache() = Unit
    // endregion

    companion object {
        val ORDERS_LIST_TTL: Duration = 600.seconds
        val ORDER_DETAIL_TTL: Duration = 300.seconds
        const val ORDERS_PAGE_LIMIT: Int = 100
    }
}
