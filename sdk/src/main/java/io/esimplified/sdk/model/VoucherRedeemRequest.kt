package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VoucherRedeemRequest(
    @SerialName("voucher_code")
    val voucherCode: String
)


