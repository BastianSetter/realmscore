package de.morzo.realmscore.data.cards

import android.content.Context
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.JokerType
import de.morzo.realmscore.domain.model.Suit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class CardLookup(private val context: Context) {

    private val cards: List<CardDefinition> by lazy { loadFromAssets() }

    fun getAll(): List<CardDefinition> = cards

    fun getByKey(key: String): CardDefinition? = cards.firstOrNull { it.key == key }

    fun search(query: String): List<CardDefinition> {
        if (query.isBlank()) return cards
        val q = query.trim().lowercase()
        return cards.filter { it.nameDe.lowercase().contains(q) }
    }

    fun filterBySuits(suits: Set<Suit>): List<CardDefinition> {
        if (suits.isEmpty()) return cards
        return cards.filter { it.suit in suits }
    }

    private fun loadFromAssets(): List<CardDefinition> {
        val raw = context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val file = json.decodeFromString<CardDataFile>(raw)
        return file.cards.map { it.toDomain() }
    }

    companion object {
        private const val ASSET_PATH = "cards/base_game.json"
        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class CardDataFile(
    val version: Int,
    val cards: List<CardDto>,
)

@Serializable
private data class CardDto(
    val key: String,
    val nameDe: String,
    val suit: String,
    val baseStrength: Int,
    val ruleTextDe: String,
    val isJoker: Boolean = false,
    val jokerType: String? = null,
) {
    fun toDomain(): CardDefinition = CardDefinition(
        key = key,
        nameDe = nameDe,
        suit = Suit.valueOf(suit),
        baseStrength = baseStrength,
        ruleTextDe = ruleTextDe,
        isJoker = isJoker,
        jokerType = jokerType?.let(JokerType::valueOf),
    )
}
