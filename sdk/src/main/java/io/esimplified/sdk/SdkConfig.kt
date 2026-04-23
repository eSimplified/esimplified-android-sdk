package io.esimplified.sdk

data class SdkConfig(
    val baseUrl: String,
    val apiVersion: String = "v2",
    val clientId: String,
    val clientSecret: String,
    val awsWafToken: String = "",
    val enableLogging: Boolean = false,
    val customHeadersProvider: (() -> Map<String, String>)? = null,
)
