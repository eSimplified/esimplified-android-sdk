package io.esimplified.sdk.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = PaymentMethodSerializer::class)
enum class PaymentMethod {
    @SerialName("stripe_intent")
    STRIPE_INTENT,

    @SerialName("stripe_checkout")
    STRIPE_CHECKOUT,

    @SerialName("agent_payment")
    AGENT_PAYMENT,

    @SerialName("complimentary")
    COMPLIMENTARY,

    @SerialName("voucher")
    VOUCHER,

    @SerialName("split_payment")
    SPLIT_PAYMENT,

    @SerialName("pay_with_points")
    PAY_WITH_POINTS,

    UNKNOWN;

    val displayName: String
        get() = when (this) {
            STRIPE_INTENT -> "Stripe"
            STRIPE_CHECKOUT -> "Stripe"
            AGENT_PAYMENT -> "Agent Payment"
            COMPLIMENTARY -> "Complimentary"
            VOUCHER -> "Voucher"
            SPLIT_PAYMENT -> "Split Payment"
            PAY_WITH_POINTS -> "Pay with Points"
            UNKNOWN -> "Payment"
        }

    val shouldShowAmount: Boolean
        get() = when (this) {
            STRIPE_INTENT, STRIPE_CHECKOUT, AGENT_PAYMENT, SPLIT_PAYMENT, PAY_WITH_POINTS -> true
            COMPLIMENTARY, VOUCHER, UNKNOWN -> false
        }
}

object PaymentMethodSerializer : KSerializer<PaymentMethod> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("PaymentMethod", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PaymentMethod) {
        val string = when (value) {
            PaymentMethod.STRIPE_INTENT -> "stripe_intent"
            PaymentMethod.STRIPE_CHECKOUT -> "stripe_checkout"
            PaymentMethod.AGENT_PAYMENT -> "agent_payment"
            PaymentMethod.COMPLIMENTARY -> "complimentary"
            PaymentMethod.VOUCHER -> "voucher"
            PaymentMethod.SPLIT_PAYMENT -> "split_payment"
            PaymentMethod.PAY_WITH_POINTS -> "pay_with_points"
            PaymentMethod.UNKNOWN -> "unknown"
        }
        encoder.encodeString(string)
    }

    override fun deserialize(decoder: Decoder): PaymentMethod {
        return when (decoder.decodeString()) {
            "stripe_intent" -> PaymentMethod.STRIPE_INTENT
            "stripe_checkout" -> PaymentMethod.STRIPE_CHECKOUT
            "agent_payment" -> PaymentMethod.AGENT_PAYMENT
            "complimentary" -> PaymentMethod.COMPLIMENTARY
            "voucher" -> PaymentMethod.VOUCHER
            "split_payment" -> PaymentMethod.SPLIT_PAYMENT
            "pay_with_points" -> PaymentMethod.PAY_WITH_POINTS
            else -> PaymentMethod.UNKNOWN
        }
    }
}
