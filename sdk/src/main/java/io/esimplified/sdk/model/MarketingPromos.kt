package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// region Marketing Promos
@Serializable
data class MarketingPromos(
    val promos: List<Promo> = emptyList(),
)
// endregion

// region Promo
@Serializable
data class Promo(
    val slug: String = "",
    val title: String = "",
    val color: String? = null,
    val image: String? = null,
    val content: String? = null,
    val faqs: List<PromoFaq> = emptyList(),
    @SerialName("cta_heading") val ctaHeading: String? = null,
    @SerialName("cta_text") val ctaText: String? = null,
    @SerialName("faq_heading") val faqHeading: String? = null,
    @SerialName("slider_image") val sliderImage: String? = null,
    @SerialName("slider_heading") val sliderHeading: String? = null,
    @SerialName("slider_subheading") val sliderSubheading: String? = null,
) {
    val destinationUrl: String?
        get() {
            val trimmed = slug.trim()
            if (trimmed.isEmpty()) return null
            return if (PROMO_SCHEME.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        }
}

private val PROMO_SCHEME = Regex("""^[a-zA-Z][a-zA-Z0-9+.\-]*://""")
// endregion

// region Promo FAQ
@Serializable
data class PromoFaq(
    val question: String = "",
    val answer: String = "",
)
// endregion
