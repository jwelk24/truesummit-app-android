package com.truesummit.android.data.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

/**
 * Encodes [UUID] as a JSON string, which is how Postgres hands uuid columns
 * back over PostgREST.
 *
 * kotlinx-serialization has no built-in serializer for java.util.UUID, so a
 * `@Serializable` class holding one fails to compile once the serialization
 * plugin is actually applied.
 */
object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.util.UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID =
        UUID.fromString(decoder.decodeString())
}
