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
        return cards.filter {
            it.nameDe.lowercase().contains(q) || it.nameEn?.lowercase()?.contains(q) == true
        }
    }

    fun filterBySuits(suits: Set<Suit>): List<CardDefinition> {
        if (suits.isEmpty()) return cards
        return cards.filter { it.suit in suits }
    }

    /**
     * Cards the Necromancer is allowed to pull from the discard pile: only Armies, Wizards,
     * Leaders, or Beasts (official rule), minus the given keys.
     */
    fun getNecromancerEligibleCards(excludeKeys: Set<String> = emptySet()): List<CardDefinition> =
        cards.filter { it.suit in NECROMANCER_SUITS && it.key !in excludeKeys }

    /**
     * Candidate cards the Necromancer may pull, given the current hand.
     *
     * Phase 17.1: the discard pile is not scanned, so we offer the full eligible set (Army/Wizard/
     * Leader/Beast, minus the cards already in hand). The [discardScanned]/[discardKeys] parameters
     * are the Phase-20 hook: when the middle is scanned, the list narrows to the captured cards.
     */
    fun getNecromancerCandidates(
        handKeys: Set<String>,
        discardScanned: Boolean = false,
        discardKeys: Set<String> = emptySet(),
    ): List<CardDefinition> {
        // PHASE 20: bei gescanntem Mittelfeld auf discardKeys filtern.
        if (discardScanned) {
            return getNecromancerEligibleCards(excludeKeys = handKeys).filter { it.key in discardKeys }
        }
        return getNecromancerEligibleCards(excludeKeys = handKeys)
    }

    private fun loadFromAssets(): List<CardDefinition> {
        val raw = context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val file = json.decodeFromString<CardDataFile>(raw)
        val overrides = loadEnOverrides()
        return file.cards.map { dto ->
            val override = overrides[dto.key]
            dto.toDomain(nameEn = override?.nameEn, ruleTextEn = override?.ruleTextEn)
        }
    }

    /**
     * Loads the optional English override file (Phase 19). Missing file or unreadable entries are
     * tolerated: cards then simply fall back to their German text.
     */
    private fun loadEnOverrides(): Map<String, CardEnOverrideDto> = runCatching {
        val raw = context.assets.open(ASSET_PATH_EN).bufferedReader(Charsets.UTF_8).use { it.readText() }
        json.decodeFromString<CardEnDataFile>(raw).cards.associateBy { it.key }
    }.getOrDefault(emptyMap())

    companion object {
        private const val ASSET_PATH = "cards/base_game.json"
        private const val ASSET_PATH_EN = "cards/base_game_en.json"
        private val json = Json { ignoreUnknownKeys = true }

        /** Suits the Necromancer may pull from the discard pile (official rule). */
        val NECROMANCER_SUITS = setOf(Suit.ARMY, Suit.WIZARD, Suit.LEADER, Suit.BEAST)
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
    fun toDomain(nameEn: String? = null, ruleTextEn: String? = null): CardDefinition = CardDefinition(
        key = key,
        nameDe = nameDe,
        suit = Suit.valueOf(suit),
        baseStrength = baseStrength,
        ruleTextDe = ruleTextDe,
        isJoker = isJoker,
        jokerType = jokerType?.let(JokerType::valueOf),
        nameEn = nameEn,
        ruleTextEn = ruleTextEn,
    )
}

@Serializable
private data class CardEnDataFile(
    val version: Int = 1,
    val cards: List<CardEnOverrideDto> = emptyList(),
)

@Serializable
private data class CardEnOverrideDto(
    val key: String,
    val nameEn: String,
    val ruleTextEn: String,
)
