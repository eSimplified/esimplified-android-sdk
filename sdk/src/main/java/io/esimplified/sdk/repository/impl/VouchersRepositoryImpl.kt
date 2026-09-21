package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.VouchersRepository

import io.esimplified.sdk.model.VoucherRedeemRequest
import io.esimplified.sdk.model.VoucherRedeemResponse
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.repository.asSdkError
import kotlinx.coroutines.CancellationException
import io.esimplified.sdk.SdkLog

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
}
