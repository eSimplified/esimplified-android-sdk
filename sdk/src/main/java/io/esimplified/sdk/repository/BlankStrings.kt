package io.esimplified.sdk.repository

/** An optional request argument: blank is sent as absent, never as an empty value. */
internal fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
