package io.esimplified.sdk.model

data class PackagesPage(
    val packages: List<PackagePlan> = emptyList(),
    val totalCount: Int = 0,
    val promoCode: CheckoutCouponResponse? = null,
)
