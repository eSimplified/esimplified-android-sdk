package io.esimplified.sdk.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationSettingsEncodingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `encodes both keys when a setting is switched off`() {
        val encoded = json.encodeToString(NotificationSettings(type = "marketing", enabled = false))

        assertEquals("""{"type":"marketing","enabled":false}""", encoded)
    }

    @Test
    fun `encodes both keys when a setting is switched on`() {
        val encoded = json.encodeToString(NotificationSettings(type = "account", enabled = true))

        assertEquals("""{"type":"account","enabled":true}""", encoded)
    }

    @Test
    fun `encodes every entry in a list with both keys`() {
        val encoded = json.encodeToString(
            listOf(
                NotificationSettings(type = "marketing", enabled = false),
                NotificationSettings(type = "account", enabled = false),
            )
        )

        assertEquals(
            """[{"type":"marketing","enabled":false},{"type":"account","enabled":false}]""",
            encoded,
        )
    }

    @Test
    fun `still decodes a payload that omits a key`() {
        val decoded = json.decodeFromString<NotificationSettings>("""{"type":"marketing"}""")

        assertEquals(NotificationSettings(type = "marketing", enabled = false), decoded)
    }
}
