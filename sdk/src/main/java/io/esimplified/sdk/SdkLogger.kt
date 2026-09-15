package io.esimplified.sdk

import android.util.Log

enum class SdkLogLevel {
    DEBUG,
    WARNING,
    ERROR,
}

fun interface SdkLogger {
    fun log(level: SdkLogLevel, message: String, throwable: Throwable?)
}

internal fun String.redactedPath(): String =
    split("/").joinToString("/") { segment ->
        if (segment.length >= 8 && segment.any { it.isDigit() }) "…" else segment
    }

internal object SdkLog {

    private const val TAG = "EsimplifiedSdk"

    @Volatile
    internal var delegate: SdkLogger? = null

    @Volatile
    internal var isEnabled: Boolean = false

    // region Levels
    fun d(message: String, throwable: Throwable? = null) = log(SdkLogLevel.DEBUG, message, throwable)

    fun w(message: String, throwable: Throwable? = null) = log(SdkLogLevel.WARNING, message, throwable)

    fun e(message: String, throwable: Throwable? = null) = log(SdkLogLevel.ERROR, message, throwable)
    // endregion

    // region Sinks
    private fun log(level: SdkLogLevel, message: String, throwable: Throwable?) {
        delegate?.let {
            it.log(level, message, throwable)
            return
        }
        if (!isEnabled) return
        when (level) {
            SdkLogLevel.DEBUG -> Log.d(TAG, message, throwable)
            SdkLogLevel.WARNING -> Log.w(TAG, message, throwable)
            SdkLogLevel.ERROR -> Log.e(TAG, message, throwable)
        }
    }
    // endregion

    internal fun resetForTesting() {
        delegate = null
        isEnabled = false
    }
}
