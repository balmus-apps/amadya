package ro.amadya.shared

import java.util.Locale
import ro.amadya.contract.model.LocalizedText as LocalizedTextDto

/**
 * Text in the supported UI languages. Persisted as JSONB (`{"ro": "...", "en": "..."}`) through [toMap] / [fromMap].
 * Romanian is mandatory; other languages fall back to it.
 */
data class LocalizedText(val ro: String, val en: String? = null) {

    fun resolve(locale: Locale): String = when (locale.language) {
        "en" -> en?.takeIf { it.isNotBlank() } ?: ro
        else -> ro
    }

    fun toMap(): MutableMap<String, String> =
        buildMap { put("ro", ro); en?.takeIf { it.isNotBlank() }?.let { put("en", it) } }.toMutableMap()

    fun toDto() = LocalizedTextDto(ro = ro, en = en)

    companion object {
        fun fromMap(map: Map<String, String>?): LocalizedText? =
            map?.get("ro")?.let { LocalizedText(it, map["en"]) }

        fun of(dto: LocalizedTextDto) = LocalizedText(dto.ro.trim(), dto.en?.trim()?.ifBlank { null })
    }
}

fun Map<String, String>.localized(locale: Locale): String =
    LocalizedText.fromMap(this)?.resolve(locale) ?: values.firstOrNull().orEmpty()
