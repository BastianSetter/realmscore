package de.morzo.realmscore.domain.scoring

import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.JokerType
import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.scoring.joker.JokerResolver
import de.morzo.realmscore.domain.scoring.rules.BaseGameRules
import de.morzo.realmscore.domain.scoring.solver.OptimalSolver
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Loads the 53-card base game JSON (and, Phase 30, the expansion "Der verfluchte Schatz") via the
 * source-tree assets path.
 */
object TestFixture {

    /** The base game (53 cards). */
    val allCards: List<CardDefinition> by lazy { loadFromAssets("base_game.json").map { it.first } }
    private val expansionEntries by lazy { loadFromAssets("expansion_cursed_hoard.json") }
    val expansionCards: List<CardDefinition> by lazy { expansionEntries.map { it.first } }

    /** Pool of a game with the new suits: base minus replaced cards plus the expansion (68 cards). */
    val newSuitsCards: List<CardDefinition> by lazy {
        val replaced = expansionEntries.mapNotNull { it.second }.toSet()
        allCards.filter { it.key !in replaced } + expansionCards
    }
    val byKey: Map<String, CardDefinition> by lazy { (allCards + expansionCards).associateBy { it.key } }

    val registry by lazy { BaseGameRules.build() }
    val jokerResolver by lazy { JokerResolver { key -> byKey[key] } }
    val engine by lazy { ScoringEngine(registry, jokerResolver) { byKey[it] } }
    val solver by lazy {
        OptimalSolver(engine, jokerResolver) { newSuits -> if (newSuits) newSuitsCards else allCards }
    }

    fun card(key: String): CardDefinition =
        byKey[key] ?: error("unknown card key: $key (test fixture)")

    fun hand(vararg keys: String): List<CardDefinition> = keys.map(::card)

    fun score(vararg keys: String): ScoringResult =
        engine.score(ScoringInput(hand(*keys)))

    private fun loadFromAssets(fileName: String): List<Pair<CardDefinition, String?>> {
        val candidatePaths = listOf(
            "src/main/assets/cards/$fileName",
            "app/src/main/assets/cards/$fileName",
            "../app/src/main/assets/cards/$fileName",
        )
        val file = candidatePaths
            .map(::File)
            .firstOrNull { it.exists() }
            ?: error("$fileName not found from ${File(".").absolutePath}")
        val raw = file.readText(Charsets.UTF_8)
        val data = Json { ignoreUnknownKeys = true }.decodeFromString<CardDataFile>(raw)
        return data.cards.map {
            CardDefinition(
                key = it.key,
                nameDe = it.nameDe,
                suit = Suit.valueOf(it.suit),
                baseStrength = it.baseStrength,
                ruleTextDe = it.ruleTextDe,
                isJoker = it.isJoker,
                jokerType = it.jokerType?.let(JokerType::valueOf),
            ) to it.replaces
        }
    }

    @Serializable
    private data class CardDataFile(val version: Int, val cards: List<CardDto>)

    @Serializable
    private data class CardDto(
        val key: String,
        val nameDe: String,
        val suit: String,
        val baseStrength: Int,
        val ruleTextDe: String,
        val isJoker: Boolean = false,
        val jokerType: String? = null,
        val replaces: String? = null,
    )
}
