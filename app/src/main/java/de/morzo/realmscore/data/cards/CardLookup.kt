package de.morzo.realmscore.data.cards

import android.content.Context
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.JokerType
import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.scoring.joker.JokerTargets
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class CardLookup(private val context: Context) {

    /** Base game (53 cards), sorted. */
    private val baseCards: List<CardDefinition> by lazy {
        loadFromAssets(ASSET_PATH, ASSET_PATH_EN).map { it.first }.sortedForPicker()
    }

    /**
     * Expansion "Der verfluchte Schatz" part 2 (Phase 30): 15 new cards + 8 replacements, each paired
     * with the base-game key it replaces (null for genuinely new cards).
     */
    private val expansionEntries: List<Pair<CardDefinition, String?>> by lazy {
        loadFromAssets(EXPANSION_ASSET_PATH, EXPANSION_ASSET_PATH_EN)
    }

    /** Pool for a game with the new suits: base minus the replaced cards plus the expansion, sorted after merging. */
    private val newSuitsCards: List<CardDefinition> by lazy {
        val replaced = expansionEntries.mapNotNull { it.second }.toSet()
        (baseCards.filter { it.key !in replaced } + expansionEntries.map { it.first }).sortedForPicker()
    }

    /**
     * Every card ever known (base + expansion). Key lookups always resolve against this so hands of
     * old / other-mode games stay readable and scorable regardless of the current game's pool.
     */
    private val byKey: Map<String, CardDefinition> by lazy {
        (baseCards + expansionEntries.map { it.first }).associateBy { it.key }
    }

    /** The base-game pool. Callers that know the game's mode use [cardsFor]. */
    fun getAll(): List<CardDefinition> = baseCards

    /** The card pool of a game: the base game, or — with the expansion's new suits — the merged 68-card pool. */
    fun cardsFor(newSuits: Boolean): List<CardDefinition> = if (newSuits) newSuitsCards else baseCards

    /** Base + expansion cards, for lookups that must know every card (e.g. OCR name matching). */
    fun getEveryCard(): List<CardDefinition> = byKey.values.toList()

    fun getByKey(key: String): CardDefinition? = byKey[key]

    fun search(query: String, newSuits: Boolean = false): List<CardDefinition> {
        val cards = cardsFor(newSuits)
        if (query.isBlank()) return cards
        val q = query.trim().lowercase()
        return cards.filter {
            it.nameDe.lowercase().contains(q) || it.nameEn?.lowercase()?.contains(q) == true
        }
    }

    fun filterBySuits(suits: Set<Suit>, newSuits: Boolean = false): List<CardDefinition> {
        val cards = cardsFor(newSuits)
        if (suits.isEmpty()) return cards
        return cards.filter { it.suit in suits }
    }

    /**
     * Cards the given Necromancer is allowed to pull from the discard pile: Armies, Wizards, Leaders
     * or Beasts (official rule; the expansion Necromancer also Undead), minus the given keys. The
     * pool follows the Necromancer's own edition (an expansion Necromancer only exists in a
     * new-suits game).
     */
    fun getNecromancerEligibleCards(
        excludeKeys: Set<String> = emptySet(),
        necromancerKey: String = BASE_NECROMANCER_KEY,
    ): List<CardDefinition> {
        val suits = JokerTargets.necromancerSuits(necromancerKey)
        return cardsFor(JokerTargets.isExpansion(necromancerKey))
            .filter { it.suit in suits && it.key !in excludeKeys }
    }

    /**
     * Candidate cards the Necromancer may pull, given the current hand.
     *
     * Phase 17.1: the discard pile is not scanned, so we offer the full eligible set (minus the cards
     * already in hand). The [discardScanned]/[discardKeys] parameters are the Phase-20 hook: when the
     * middle is scanned, the list narrows to the captured cards.
     */
    fun getNecromancerCandidates(
        handKeys: Set<String>,
        discardScanned: Boolean = false,
        discardKeys: Set<String> = emptySet(),
        necromancerKey: String = BASE_NECROMANCER_KEY,
    ): List<CardDefinition> {
        val eligible = getNecromancerEligibleCards(excludeKeys = handKeys, necromancerKey = necromancerKey)
        return if (discardScanned) eligible.filter { it.key in discardKeys } else eligible
    }

    private fun loadFromAssets(path: String, pathEn: String): List<Pair<CardDefinition, String?>> {
        val raw = context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val file = json.decodeFromString<CardDataFile>(raw)
        val overrides = loadEnOverrides(pathEn)
        return file.cards.map { dto ->
            val override = overrides[dto.key]
            dto.toDomain(nameEn = override?.nameEn, ruleTextEn = override?.ruleTextEn) to dto.replaces
        }
    }

    // Sort by suit, then alphabetically by German name. Every consumer (CardPicker, Sandbox,
    // Necromancer candidates, …) reads these lists, so they all inherit the ordering. Applied AFTER
    // merging base + expansion so expansion cards sit inside their suit, not at the end.
    private fun List<CardDefinition>.sortedForPicker(): List<CardDefinition> =
        sortedWith(compareBy({ it.suit.ordinal }, { it.nameDe.lowercase() }))

    /**
     * Loads an optional English override file (Phase 19). Missing file or unreadable entries are
     * tolerated: cards then simply fall back to their German text.
     */
    private fun loadEnOverrides(path: String): Map<String, CardEnOverrideDto> = runCatching {
        val raw = context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        json.decodeFromString<CardEnDataFile>(raw).cards.associateBy { it.key }
    }.getOrDefault(emptyMap())

    companion object {
        private const val ASSET_PATH = "cards/base_game.json"
        private const val ASSET_PATH_EN = "cards/base_game_en.json"
        private const val EXPANSION_ASSET_PATH = "cards/expansion_cursed_hoard.json"
        private const val EXPANSION_ASSET_PATH_EN = "cards/expansion_cursed_hoard_en.json"
        private const val BASE_NECROMANCER_KEY = "necromancer"
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
    /** Phase 30: base-game card this expansion card replaces in a new-suits game. */
    val replaces: String? = null,
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
