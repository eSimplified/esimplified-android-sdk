package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.SdkLog
import io.esimplified.sdk.model.GiftCardPreviewRequest
import io.esimplified.sdk.model.GiftCardPreviewResponse
import io.esimplified.sdk.model.GiftCardRedeemRequest
import io.esimplified.sdk.model.GiftCardRedeemResponse
import io.esimplified.sdk.model.VoucherRedeemRequest
import io.esimplified.sdk.model.VoucherRedeemResponse
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.repository.VouchersRepository
import io.esimplified.sdk.repository.asSdkError
import kotlinx.coroutines.CancellationException

internal class VouchersRepositoryImpl(
    private val apiService: ApiService
) : VouchersRepository {

    // region Vouchers
    override suspend fun redeemVoucher(code: String): Result<VoucherRedeemResponse> {
        return try {
            SdkLog.d("Attempting to redeem a voucher")
            val response = apiService.redeemVoucher(VoucherRedeemRequest(voucherCode = code))
            SdkLog.d("Voucher redemption returned redeemed=${response.redeemed}")
            Result.success(response)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            SdkLog.e("Error redeeming voucher", failure)
            Result.failure(failure.asSdkError())
        }
    }
    // endregion

    // region Gift cards
    override suspend fun previewGiftCard(pin: String): Result<GiftCardPreviewResponse> {
        return try {
            SdkLog.d("Previewing a gift card")
            val response = apiService.previewGiftCard(GiftCardPreviewRequest(pin = pin))
            SdkLog.d("Gift card preview returned package ${response.giftCardPackage.packageTypeId}")
            Result.success(response)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            SdkLog.e("Error previewing gift card", failure)
            Result.failure(failure.asSdkError())
        }
    }

    override suspend fun redeemGiftCard(request: GiftCardRedeemRequest): Result<GiftCardRedeemResponse> {
        return try {
            SdkLog.d("Redeeming a gift card")
            val response = apiService.redeemGiftCard(request)
            SdkLog.d(
                "Gift card redemption returned redeemed=${response.isRedeemed}" +
                    " awaitsEmailVerification=${response.awaitsEmailVerification}"
            )
            Result.success(response)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            SdkLog.e("Error redeeming gift card", failure)
            Result.failure(failure.asSdkError())
        }
    }
    // endregion
}
