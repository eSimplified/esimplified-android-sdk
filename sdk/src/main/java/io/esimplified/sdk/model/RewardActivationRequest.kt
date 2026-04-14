package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RewardActivationRequest(
    @SerialName("reward_type") val rewardType: String
)
