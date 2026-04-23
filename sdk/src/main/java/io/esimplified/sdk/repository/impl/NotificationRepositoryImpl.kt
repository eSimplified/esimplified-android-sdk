package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.NotificationRepository

import io.esimplified.sdk.model.ApiErrorResponse
import io.esimplified.sdk.model.NotificationSettings
import io.esimplified.sdk.network.ApiService
import kotlinx.serialization.json.Json
import retrofit2.HttpException

internal class NotificationRepositoryImpl(
    private val apiService: ApiService
) : NotificationRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // region Notification Settings
    override suspend fun getSettings(): List<NotificationSettings> {
        try {
            return apiService.getNotificationSettings()
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }

    override suspend fun updateSettings(settings: List<NotificationSettings>) {
        try {
            apiService.updateNotificationSettings(settings)
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }
    // endregion

    private fun parseHttpError(e: HttpException): String? {
        return try {
            val errorBody = e.response()?.errorBody()?.string()
            if (errorBody != null) {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(errorBody)
                errorResponse.detail ?: errorResponse.message ?: errorResponse.error
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
