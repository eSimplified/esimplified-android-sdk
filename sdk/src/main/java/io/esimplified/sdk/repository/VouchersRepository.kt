package io.esimplified.sdk.repository

import io.esimplified.sdk.model.GiftCardPreviewResponse
import io.esimplified.sdk.model.GiftCardRedeemRequest
import io.esimplified.sdk.model.GiftCardRedeemResponse
import io.esimplified.sdk.model.VoucherRedeemResponse

interface VouchersRepository {
    suspend fun redeemVoucher(code: String): Result<VoucherRedeemResponse>

    suspend fun previewGiftCard(pin: String): Result<GiftCardPreviewResponse> {
        throw UnsupportedOperationException("previewGiftCard is not implemented")
    }

    suspend fun redeemGiftCard(request: GiftCardRedeemRequest): Result<GiftCardRedeemResponse> {
        throw UnsupportedOperationException("redeemGiftCard is not implemented")
    }
}
