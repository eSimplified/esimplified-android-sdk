package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.model.NotificationSettings
import io.esimplified.sdk.network.ApiService

internal class NotificationRepositoryImpl(
    private val apiService: ApiService
) : NotificationRepository {

    // region Notification Settings
    override suspend fun getSettings(): List<NotificationSettings> {
        return apiService.getNotificationSettings()
    }

    override suspend fun updateSettings(settings: List<NotificationSettings>) {
        apiService.updateNotificationSettings(settings)
    }
    // endregion
}
