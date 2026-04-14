import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchBody(

    @SerialName("search_term")
    val searchQuery: String,
)
