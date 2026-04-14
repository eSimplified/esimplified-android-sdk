package io.esimplified.sdk.network

import io.esimplified.sdk.model.AssignedEsim
import io.esimplified.sdk.model.ChangePasswordResponse
import io.esimplified.sdk.model.CheckStockResponse
import io.esimplified.sdk.model.CheckoutCouponRequest
import io.esimplified.sdk.model.CheckoutCouponResponse
import io.esimplified.sdk.model.CustomerChangePassword
import io.esimplified.sdk.model.CustomerDetails
import io.esimplified.sdk.model.CustomerForgetPassword
import io.esimplified.sdk.model.CustomerForgetPasswordResponse
import io.esimplified.sdk.model.NotificationSettings
import io.esimplified.sdk.model.UpdateCustomerPreferencesRequest
import io.esimplified.sdk.model.PaymentRequest
import io.esimplified.sdk.model.PaymentResponse
import io.esimplified.sdk.model.ProfileResponse
import io.esimplified.sdk.model.UserLocationResponse
import io.esimplified.sdk.model.VerifyEmailRequest
import io.esimplified.sdk.model.VerifyEmailResponse
import io.esimplified.sdk.model.GetTokenIntrospectResponse
import io.esimplified.sdk.model.GetTokenResponse
import io.esimplified.sdk.model.OrderHistoryItem
import io.esimplified.sdk.model.KredsLoyaltyBalanceResponse
import io.esimplified.sdk.model.Country
import io.esimplified.sdk.model.Customer
import io.esimplified.sdk.model.DeleteProfileResponse
import io.esimplified.sdk.model.OrderDetail
import io.esimplified.sdk.model.OrderInfo
import io.esimplified.sdk.model.PackagePlan
import io.esimplified.sdk.model.RatingApiResponse
import io.esimplified.sdk.model.VisaRewardsIframeResponse
import io.esimplified.sdk.model.VisaRewardsResponse
import io.esimplified.sdk.model.VoucherRedeemRequest
import io.esimplified.sdk.model.VoucherRedeemResponse
import io.esimplified.sdk.network.BaseResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query


internal interface ApiService {
    @POST("auth/token/")
    @FormUrlEncoded
    @Headers("Accept: application/json")
    suspend fun getAuthToken(
        @Field("grant_type") grantType: String,
        @Field("username") username: String? = null,
        @Field("password") password: String? = null,
        @Field("refresh_token") refreshToken: String? = null,
    ): Response<GetTokenResponse>

    @FormUrlEncoded
    @POST("auth/token/")
    @Headers("Accept: application/json")
    suspend fun getSignInWith(
        @Field("grant_type") grantType: String,
        @Field("provider_account_id") providerAccountId: String,
        @Field("full_name") fullName: String,
        @Field("phone_number") phoneNumber: String,
        @Field("provider") provider: String,
        @Field("first_name") firstName: String,
        @Field("last_name") lastName: String,
        @Field("email") email: String,
        @Field("id_token") idToken: String,
        @Field("sender") sender: String,
    ): GetTokenResponse


    @POST("auth/token/")
    @Headers("Accept: application/json")
    suspend fun getTokenIntrospect(
        @Query("token") token: String? = null,
    ): GetTokenIntrospectResponse


    @POST("api/v2/register/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun register(@Body data: CustomerDetails): ProfileResponse

    @GET("api/v2/customer/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun getUser(): Customer

    @PATCH("api/v2/customer/edit/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun update(@Body data: CustomerDetails): ProfileResponse

    @PATCH("api/v2/customer/preferences/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun updatePreferences(@Body data: UpdateCustomerPreferencesRequest): Customer

    @DELETE("api/v2/customer/delete/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun deleteProfile(): DeleteProfileResponse

    @POST("api/v2/customer/reset-password/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun changePassword(@Body data: CustomerChangePassword): ChangePasswordResponse

    @POST("api/v2/verify-email/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun verifyEmail(@Body data: VerifyEmailRequest): VerifyEmailResponse

    @POST("api/v2/forgot-password/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun forgetPassword(@Body data: CustomerForgetPassword): CustomerForgetPasswordResponse

    @POST("api/v2/payments/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun getCheckoutPaymentIntent(@Body data: PaymentRequest): PaymentResponse

    @GET("api/v2/customer/promotions/promo_code/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun getCheckoutCoupon(): CheckoutCouponResponse

    @POST("api/v2/customer/promotions/promo_code/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun addCheckoutCoupon(
        @Body data: CheckoutCouponRequest
    ): Response<CheckoutCouponResponse>

    @DELETE("api/v2/customer/promotions/promo_code/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun removeCheckoutCoupon(): CheckoutCouponResponse

    @GET("api/v2/countries/?limit=300")
    suspend fun getCountryList(): BaseResponse<List<Country>>

    @GET("api/v2/countries/")
    suspend fun getCountryListBy(
        @Query("country_code") code: String? = null,
        @Query("country_name") name: String? = null,
        @Query("region") region: String? = null,
    ): BaseResponse<List<Country>>

    @GET("api/v2/packages/")
    suspend fun getPackageListBy(
        @Query("country_code") code: String? = null,
        @Query("country_name") name: String? = null,
        @Query("country_name_slug") slug: String? = null,
        @Query("reverse_order") reverseOrder: String = "true"
    ): BaseResponse<List<PackagePlan>>

    @GET("api/v2/customer/esims/{iccid}/top-up/packages/")
    suspend fun getEsimTopUpPackages(
        @Path("iccid") iccid: String, @Query("reverse_order") reverseOrder: String = "true"
    ): BaseResponse<List<PackagePlan>>

    @GET("api/v2/check-stock/")
    suspend fun getPackageStock(
        @Query("package_type_id") packageTypeId: Int?,
        @Query("package_slug") packageSlug: String? = null,
        @Query("country_slug") countrySlug: String? = null,
    ): CheckStockResponse

    @GET("api/v2/search/")
    suspend fun search(
        @Query("search_term") query: String? = null,
    ): BaseResponse<List<Country>>

    @GET("api/v2/customer/esims/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun getCustomerEsimList(
        @Query("show_esim_details") getESimDetails: Boolean? = null,
        @Query("show_package_details") getPackageDetails: Boolean? = null,
        @Query("show_balance_remaining") getBalanceRemaining: Boolean? = null,
        @Query("show_archived_esims") showArchived: Boolean? = null
    ): BaseResponse<List<AssignedEsim>>

    @GET("api/v2/customer/esims/{iccid}/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun getCustomerEsimByICCID(
        @Path("iccid") iccid: String,
        @Query("show_esim_details") getESimDetails: Boolean? = null,
        @Query("show_package_details") getPackageDetails: Boolean? = null,
        @Query("show_balance_remaining") getBalanceRemaining: Boolean? = null,
    ): AssignedEsim

    @GET("api/v2/customer/orders/{order_uuid}/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun getOrderInfoBy(
        @Path("order_uuid") id: String,
        @Query("include_base64_qr_code") encodeQRCode: Boolean = true
    ): OrderInfo

    @GET("api/v2/customer/orders/{order_uuid}")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun getOrderDetails(
        @Path("order_uuid") id: String,
        @Query("show_esim_details") esimStatus: Boolean,
        @Query("include_base64_qr_code") encodeQRCode: Boolean,
    ): OrderDetail

    @POST("api/v2/customer/orders/{order_uuid}")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun getOrderStatus(
        @Path("order_uuid") id: String,
    )

    @GET("api/v2/customer/orders/" )
    suspend fun getOrderHistory(
        @Query("used_points") usedPoints: Boolean? = null
    ): BaseResponse<List<OrderHistoryItem>>
    @GET("api/v2/customer/loyalty/" )
    suspend fun getLoyaltyPoints(): KredsLoyaltyBalanceResponse

    @POST("api/v2/customer/promotions/iframe/")
    suspend fun getPromotionIframe(): VisaRewardsIframeResponse

    @GET("api/v2/customer/promotions/validate/{token}")
    suspend fun validatePromotion(@Path("token") token: String): VisaRewardsResponse

    @GET("api/v2/get_country/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun getUserLocation(): UserLocationResponse

    @FormUrlEncoded
    @PATCH("api/v2/customer/promotions/validate/{token}")
    suspend fun activatePromotion(
        @Path("token") token: String, @Field("reward_type") rewardCode: String
    ): VisaRewardsResponse

    @FormUrlEncoded
    @PUT("api/v2/customer/esims/{iccid}/")
    suspend fun updateEsim(
        @Path("iccid") id: String,
        @Field("auto_top_up") autoTopUp: Boolean? = null,
        @Field("archived") isArchived: Boolean? = null,
        @Field("esim_name") name: String? = null,
    ): Response<ResponseBody?>

    @GET("api/v2/reviews/?type=store_review")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun getPackageRating(): RatingApiResponse

    @GET("api/v2/customer/notifications/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun getNotificationSettings(): List<NotificationSettings>

    @PATCH("api/v2/customer/notifications/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun updateNotificationSettings(@Body data: List<NotificationSettings>)

    @POST("api/v2/customer/promotions/voucher/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun redeemVoucher(@Body data: VoucherRedeemRequest): VoucherRedeemResponse

    @POST("api/v2/payments/quote/")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun sendKredsQuote(@Body data: io.esimplified.sdk.model.KredsQuoteRequest): io.esimplified.sdk.model.KredsQuoteResponse
}
