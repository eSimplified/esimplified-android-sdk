package io.esimplified.sdk.network

import kotlinx.serialization.Serializable

@Serializable
internal data class BaseResponse<T>(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: T
)
