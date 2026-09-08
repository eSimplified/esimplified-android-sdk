package io.esimplified.sdk.repository

import io.esimplified.sdk.network.SdkError

data class RepositoryResult<T>(
    val value: T,
    val isStale: Boolean = false,
    val failure: SdkError? = null,
) {
    val didFail: Boolean get() = failure != null

    val isOffline: Boolean get() = failure?.isOffline == true
}
