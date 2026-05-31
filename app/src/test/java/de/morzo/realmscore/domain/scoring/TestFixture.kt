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

/** Loads the 53-card base game JSON via the source-tree assets path. */
object TestFixture {

    val allCards: List<CardDefinition> by lazy { loadFromAssets() }
    val byKey: Map<String, CardDefinition> by lazy { allCards.associateBy { it.key } }

    val registry by lazy { BaseGameRules.build() }
    val jokerResolver by lazy { JokerResolver { key -> byKey[key] } }
    val engine by lazy { ScoringEngine(registry, jokerResolver) { byKey[it] } }
    val solver by lazy { OptimalSolver(engine, jokerResolver, allCards) }

    fun card(key: String): CardDefinition =
        byKey[key] ?: error("unknown card key: $key (test fixture)")

    fun hand(vararg keys: String): List<CardDefinition> = keys.map(::card)

    fun score(vararg keys: String): ScoringResult =
        engine.score(ScoringInput(hand(*keys)))

    private fun loadFromAssets(): List<CardDefinition> {
        val candidatePaths = listOf(
            "src/main/assets/cards/base_game.json",
            "app/src/main/assets/cards/base_game.json",
            "../app/src/main/assets/cards/base_game.json",
        )
        val file = candidatePaths
            .map(::File)
            .firstOrNull { it.exists() }
            ?: error("base_game.json not found from ${File(".").absolutePath}")
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
            )
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
    )
}
