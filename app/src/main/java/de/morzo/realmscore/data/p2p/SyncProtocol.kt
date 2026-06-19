package de.morzo.realmscore.data.p2p

import de.morzo.realmscore.domain.p2p.model.HandshakePayload
import de.morzo.realmscore.domain.p2p.model.SyncMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * (De)serialization for the P2P wire protocol (Phase 28). One [SyncMessage] per newline-terminated
 * JSON line; [encode] therefore must not emit embedded newlines, so `prettyPrint` is **off**.
 *
 * `classDiscriminator = "type"` controls the polymorphic tag for the [SyncMessage] sealed hierarchy.
 * `ignoreUnknownKeys` keeps an older peer forward-compatible if a newer one adds fields.
 */
object SyncProtocol {

    val json: Json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    /** Encodes a message to a single-line JSON string (no trailing newline). */
    fun encode(message: SyncMessage): String = json.encodeToString(message)

    /** Decodes one JSON line back into a [SyncMessage]. Throws on malformed input. */
    fun decode(line: String): SyncMessage = json.decodeFromString(line)

    /** Handshake payloads are exchanged out-of-band (QR/code), not over the message stream. */
    fun encodeHandshake(payload: HandshakePayload): String = json.encodeToString(payload)

    fun decodeHandshake(text: String): HandshakePayload = json.decodeFromString(text)
}
