package io.esimplified.sdk.repository

import io.esimplified.sdk.model.NotificationSettings

interface NotificationRepository {
    suspend fun getSettings(): List<NotificationSettings>
    suspend fun updateSettings(settings: List<NotificationSettings>)
}
