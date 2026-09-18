package io.esimplified.sdk.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketingPromosDecodingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    // region Full payload
    @Test
    fun `a full promo decodes every field`() {
        val promo = decode(FULL).promos.single()

        assertEquals("https://knowroaming.vercel.app/kreds", promo.slug)
        assertEquals("Got Kreds? Save on data.", promo.title)
        assertEquals("#1E1E1E", promo.color)
        assertEquals("https://cdn.sanity.io/promo.webp", promo.image)
        assertEquals("That data plan for your next trip?", promo.content)
        assertEquals("Your Kreds are ready to be spent.", promo.ctaHeading)
        assertEquals("Check your balance", promo.ctaText)
        assertEquals("Buy, and earn.", promo.faqHeading)
        assertEquals("https://cdn.sanity.io/slider.webp", promo.sliderImage)
        assertEquals("Got Kreds?", promo.sliderHeading)
        assertEquals("Go save. Simply apply at checkout.", promo.sliderSubheading)
        assertEquals(1, promo.faqs.size)
        assertEquals("How do I earn Kreds?", promo.faqs.first().question)
        assertEquals("Every purchase earns them.", promo.faqs.first().answer)
    }
    // endregion

    // region Defensive decoding
    @Test
    fun `an explicit null slider heading and subheading do not fail the payload`() {
        val promo = decode(
            """
            {
              "promos": [
                {
                  "slug": "https://knowroaming.vercel.app/refund",
                  "title": "Refunds",
                  "slider_image": "https://cdn.sanity.io/refund.webp",
                  "slider_heading": null,
                  "slider_subheading": null
                }
              ]
            }
            """.trimIndent()
        ).promos.single()

        assertEquals("Refunds", promo.title)
        assertEquals("https://cdn.sanity.io/refund.webp", promo.sliderImage)
        assertNull(promo.sliderHeading)
        assertNull(promo.sliderSubheading)
    }

    @Test
    fun `a promo carrying only a slug and a title decodes with null optionals and no faqs`() {
        val promo = decode(
            """{ "promos": [ { "slug": "kreds", "title": "Kreds" } ] }"""
        ).promos.single()

        assertEquals("kreds", promo.slug)
        assertEquals("Kreds", promo.title)
        assertNull(promo.color)
        assertNull(promo.image)
        assertNull(promo.content)
        assertNull(promo.ctaHeading)
        assertNull(promo.ctaText)
        assertNull(promo.faqHeading)
        assertNull(promo.sliderImage)
        assertNull(promo.sliderHeading)
        assertNull(promo.sliderSubheading)
        assertTrue(promo.faqs.isEmpty())
    }

    @Test
    fun `a promo with neither slug nor title falls back to empty strings`() {
        val promo = decode("""{ "promos": [ { "color": "#FFF" } ] }""").promos.single()

        assertEquals("", promo.slug)
        assertEquals("", promo.title)
        assertEquals("#FFF", promo.color)
    }

    @Test
    fun `a missing promos key decodes to an empty list`() {
        assertTrue(decode("{}").promos.isEmpty())
    }

    @Test
    fun `an faq missing its answer decodes to an empty answer`() {
        val faq = decode(
            """{ "promos": [ { "faqs": [ { "question": "Why?" } ] } ] }"""
        ).promos.single().faqs.single()

        assertEquals("Why?", faq.question)
        assertEquals("", faq.answer)
    }
    // endregion

    // region Destination URL
    @Test
    fun `a bare host slug is promoted to https`() {
        assertEquals("https://knowroaming.com/kreds", Promo(slug = "knowroaming.com/kreds").destinationUrl)
    }

    @Test
    fun `a slug that already carries a scheme is left alone`() {
        assertEquals("https://knowroaming.com/kreds", Promo(slug = "https://knowroaming.com/kreds").destinationUrl)
        assertEquals("http://knowroaming.com", Promo(slug = "http://knowroaming.com").destinationUrl)
    }

    @Test
    fun `a slug is trimmed before it becomes a url`() {
        assertEquals("https://knowroaming.com", Promo(slug = "  knowroaming.com  ").destinationUrl)
    }

    @Test
    fun `an empty slug yields no destination url`() {
        assertNull(Promo().destinationUrl)
        assertNull(Promo(slug = "   ").destinationUrl)
    }
    // endregion

    private fun decode(body: String): MarketingPromos =
        json.decodeFromString(MarketingPromos.serializer(), body)

    private companion object {
        val FULL = """
            {
              "promos": [
                {
                  "slug": "https://knowroaming.vercel.app/kreds",
                  "title": "Got Kreds? Save on data.",
                  "color": "#1E1E1E",
                  "image": "https://cdn.sanity.io/promo.webp",
                  "content": "That data plan for your next trip?",
                  "cta_heading": "Your Kreds are ready to be spent.",
                  "cta_text": "Check your balance",
                  "faq_heading": "Buy, and earn.",
                  "faqs": [
                    { "question": "How do I earn Kreds?", "answer": "Every purchase earns them." }
                  ],
                  "slider_image": "https://cdn.sanity.io/slider.webp",
                  "slider_heading": "Got Kreds?",
                  "slider_subheading": "Go save. Simply apply at checkout."
                }
              ]
            }
        """.trimIndent()
    }
}
