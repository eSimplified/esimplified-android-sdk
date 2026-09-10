package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// region Store review response
@Serializable
data class RatingApiResponse(
    @SerialName("store_name")
    val storeName: String = "",
    @SerialName("review_count")
    val reviewCount: Int = 0,
    @SerialName("results_count")
    val resultsCount: Int = 0,
    @SerialName("verdict")
    val verdict: String = "",
    @SerialName("average_rating")
    val rating: Double?,
    @SerialName("reviews")
    val reviews: List<Review>? = null,
    @SerialName("stats")
    val stats: Stats? = null,
)
// endregion

// region Review
@Serializable
data class Review(
    @SerialName("type")
    val type: String? = null,
    @SerialName("type_label")
    val typeLabel: String? = null,
    @SerialName("rating")
    val rating: Int? = null,
    @SerialName("title")
    val title: String? = null,
    @SerialName("comments")
    val comments: String? = null,
    @SerialName("author")
    val author: Author? = null,
    @SerialName("date_created")
    val dateCreated: String? = null,
    @SerialName("time_ago")
    val timeAgo: String? = null,
    @SerialName("sku")
    val sku: String? = null,
)
// endregion

// region Author
@Serializable
data class Author(
    @SerialName("name")
    val name: String? = null,
    @SerialName("location")
    val location: String? = null,
)
// endregion

// region Stats
@Serializable
data class Stats(
    @SerialName("company")
    val company: CompanyStats? = null,
    @SerialName("ratings")
    val ratings: Ratings? = null,
)
// endregion

// region Company stats
@Serializable
data class CompanyStats(
    @SerialName("review_count")
    val reviewCount: Int = 0,
    @SerialName("average_rating")
    val averageRating: String = "",
)
// endregion

// region Ratings
@Serializable
data class Ratings(
    @SerialName("4")
    val four: Int? = null,
    @SerialName("5")
    val five: Int? = null,
)
// endregion
