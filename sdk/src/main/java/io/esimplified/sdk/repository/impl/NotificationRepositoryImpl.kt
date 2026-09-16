package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.NotificationRepository

import io.esimplified.sdk.model.NotificationSettings
import io.esimplified.sdk.network.ApiErrorMessage
import io.esimplified.sdk.network.ApiService
import retrofit2.HttpException

internal class NotificationRepositoryImpl(
    private val apiService: ApiService
) : NotificationRepository {

    // region Notification Settings
    override suspend fun getSettings(): List<NotificationSettings> {
        try {
            return apiService.getNotificationSettings()
        } catch (e: HttpException) {
            throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
        }
    }

    override suspend fun updateSettings(settings: List<NotificationSettings>) {
        try {
            apiService.updateNotificationSettings(settings)
        } catch (e: HttpException) {
            throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
        }
    }
    // endregion

}
