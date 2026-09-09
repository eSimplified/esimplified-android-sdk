package io.esimplified.sdk.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiErrorMessageTest {

    // region Flat keys
    @Test
    fun `message is used on its own`() {
        assertEquals("Card declined", ApiErrorMessage.parse("""{"message":"Card declined"}"""))
    }

    @Test
    fun `detail is used on its own`() {
        assertEquals("Not found", ApiErrorMessage.parse("""{"detail":"Not found"}"""))
    }

    @Test
    fun `error is used on its own`() {
        assertEquals("invalid_grant", ApiErrorMessage.parse("""{"error":"invalid_grant"}"""))
    }

    @Test
    fun `message wins over detail and error`() {
        val body = """{"message":"m","detail":"d","error":"e"}"""

        assertEquals("m", ApiErrorMessage.parse(body))
    }

    @Test
    fun `detail wins over error when message is absent`() {
        assertEquals("d", ApiErrorMessage.parse("""{"detail":"d","error":"e"}"""))
    }

    @Test
    fun `a null message falls through to detail`() {
        assertEquals("d", ApiErrorMessage.parse("""{"message":null,"detail":"d"}"""))
    }
    // endregion

    // region Validation bodies
    @Test
    fun `a validation body is flattened into a labelled line`() {
        val body = """{"first_name":["This field is required."]}"""

        assertEquals("First name: This field is required.", ApiErrorMessage.parse(body))
    }

    @Test
    fun `multiple keys are labelled and joined sorted by key`() {
        val body = """{"last_name":["Too long."],"first_name":["This field is required."]}"""

        assertEquals(
            "First name: This field is required.\nLast name: Too long.",
            ApiErrorMessage.parse(body),
        )
    }

    @Test
    fun `array elements are flattened under the same key`() {
        val body = """{"phone_number":["Too short.","Wrong country."]}"""

        assertEquals(
            "Phone number: Too short.\nPhone number: Wrong country.",
            ApiErrorMessage.parse(body),
        )
    }

    @Test
    fun `nested objects recurse and sort by key`() {
        val body = """{"address":{"post_code":["Required."],"city":["Required."]}}"""

        assertEquals("City: Required.\nPost code: Required.", ApiErrorMessage.parse(body))
    }

    @Test
    fun `non_field_errors yields the value alone`() {
        val body = """{"non_field_errors":["Passwords do not match."]}"""

        assertEquals("Passwords do not match.", ApiErrorMessage.parse(body))
    }

    @Test
    fun `unlabelled keys yield the value alone`() {
        val body = """{"errors":["Boom."],"detail":["Bang."]}"""

        assertEquals("Bang.\nBoom.", ApiErrorMessage.parse(body))
    }

    @Test
    fun `unlabelled and labelled keys mix in key order`() {
        val body = """{"non_field_errors":["Bad combination."],"first_name":["Required."]}"""

        assertEquals("First name: Required.\nBad combination.", ApiErrorMessage.parse(body))
    }

    @Test
    fun `non string values are dropped`() {
        val body = """{"code":42,"ok":false,"reason":["Nope."]}"""

        assertEquals("Reason: Nope.", ApiErrorMessage.parse(body))
    }
    // endregion

    // region Fallbacks
    @Test
    fun `plain text is used as trimmed text`() {
        assertEquals("Service unavailable", ApiErrorMessage.parse("  Service unavailable\n"))
    }

    @Test
    fun `an empty body falls back to unknown error`() {
        assertEquals("Unknown error", ApiErrorMessage.parse(""))
    }

    @Test
    fun `a null body falls back to unknown error`() {
        assertEquals("Unknown error", ApiErrorMessage.parse(null))
    }

    @Test
    fun `json with nothing to say falls back to unknown error`() {
        assertEquals("Unknown error", ApiErrorMessage.parse("{}"))
        assertEquals("Unknown error", ApiErrorMessage.parse("[]"))
    }

    @Test
    fun `parseOrNull returns null when there is nothing to say`() {
        assertNull(ApiErrorMessage.parseOrNull(null))
        assertNull(ApiErrorMessage.parseOrNull(""))
        assertNull(ApiErrorMessage.parseOrNull("{}"))
    }

    @Test
    fun `parseOrNull returns the parsed message when there is one`() {
        assertEquals("Card declined", ApiErrorMessage.parseOrNull("""{"message":"Card declined"}"""))
    }
    // endregion
}
