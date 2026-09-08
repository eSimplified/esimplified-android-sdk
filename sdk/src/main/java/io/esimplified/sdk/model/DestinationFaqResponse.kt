package io.esimplified.sdk.model

import kotlinx.serialization.Serializable

// region Destination FAQ response
@Serializable
data class DestinationFaqResponse(
    val slug: String,
    val name: String,
    val language: String,
    val faqs: List<Faq>,
)
// endregion

// region FAQ
@Serializable
data class Faq(
    val question: String,
    val answer: String,
)
// endregion
