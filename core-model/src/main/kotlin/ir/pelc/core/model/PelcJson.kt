package ir.pelc.core.model

import kotlinx.serialization.json.Json

/** Single JSON configuration used everywhere (.pelc files, exports, AI payloads). */
object PelcJson {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(document: PelcDocument): String = json.encodeToString(PelcDocument.serializer(), document)

    fun decode(text: String): PelcDocument = json.decodeFromString(PelcDocument.serializer(), text)
}
