package io.esimplified.sdk

enum class SdkEnvironment(internal val subdomain: String) {
    STAGING("stage"),
    PRODUCTION("live"),
    ;

    internal fun baseUrl(clientName: String): String =
        "https://$clientName.$subdomain.esimplified.io"
}

data class SdkConfig internal constructor(
    val environment: SdkEnvironment?,
    val clientName: String?,
    internal val baseUrlOverride: String?,
    val apiVersion: String,
    val clientId: String,
    val clientSecret: String,
    val awsWafToken: String,
    val enableLogging: Boolean,
    val customHeadersProvider: (() -> Map<String, String>)?,
) {
    constructor(
        environment: SdkEnvironment,
        clientName: String,
        apiVersion: String = "v2",
        clientId: String,
        clientSecret: String,
        awsWafToken: String = "",
        enableLogging: Boolean = false,
        customHeadersProvider: (() -> Map<String, String>)? = null,
    ) : this(environment, clientName, null, apiVersion, clientId, clientSecret, awsWafToken, enableLogging, customHeadersProvider)

    internal val baseUrl: String get() = baseUrlOverride
        ?: environment?.baseUrl(clientName ?: error("SdkConfig requires a clientName"))
        ?: error("SdkConfig requires either an environment or a baseUrlOverride")

    internal companion object {
        /** For tests only — allows a custom base URL (e.g. MockWebServer). */
        fun forTesting(
            baseUrl: String,
            clientId: String = "test-client",
            clientSecret: String = "test-secret",
            awsWafToken: String = "",
        ) = SdkConfig(
            environment = null,
            clientName = null,
            baseUrlOverride = baseUrl,
            apiVersion = "v2",
            clientId = clientId,
            clientSecret = clientSecret,
            awsWafToken = awsWafToken,
            enableLogging = false,
            customHeadersProvider = null,
        )
    }
}
