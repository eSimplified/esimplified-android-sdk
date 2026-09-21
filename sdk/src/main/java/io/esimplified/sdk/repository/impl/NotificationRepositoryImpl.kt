package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.NotificationRepository

import io.esimplified.sdk.model.NotificationSettings
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.repository.apiRead

internal class NotificationRepositoryImpl(
    private val apiService: ApiService
) : NotificationRepository {

    // region Notification Settings
    override suspend fun getSettings(): List<NotificationSettings> =
        apiRead { apiService.getNotificationSettings() }

    override suspend fun updateSettings(settings: List<NotificationSettings>) {
        apiRead { apiService.updateNotificationSettings(settings) }
    }
    // endregion

}
