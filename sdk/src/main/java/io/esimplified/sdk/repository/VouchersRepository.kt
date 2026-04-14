package io.esimplified.sdk.repository

import io.esimplified.sdk.model.VoucherRedeemResponse

interface VouchersRepository {
    suspend fun redeemVoucher(code: String): Result<VoucherRedeemResponse>
}
