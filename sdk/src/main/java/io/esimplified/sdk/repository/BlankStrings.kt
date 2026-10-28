package io.esimplified.sdk.repository

internal fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
