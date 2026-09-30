package de.morzo.realmscore.data.cards

import android.content.Context
import de.morzo.realmscore.domain.model.CursedItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The 24 cursed items of the expansion "Der verfluchte Schatz" (Phase 30, part 1). They are never
 * hand cards: a player only records which ones they used, and their fixed points are added to the
 * round score.
 */
class CursedItemLookup(private val context: Context) {

    private val items: List<CursedItem> by lazy { load() }
    private val byKey: Map<String, CursedItem> by lazy { items.associateBy { it.key } }

    fun getAll(): List<CursedItem> = items

    fun getByKey(key: String): CursedItem? = byKey[key]

    /** Summed points of [keys] for a game with [playerCount] players (unknown keys count 0). */
    fun pointsFor(keys: Collection<String>, playerCount: Int): Int =
        keys.sumOf { byKey[it]?.pointsFor(playerCount) ?: 0 }

    private fun load(): List<CursedItem> {
        val raw = context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return json.decodeFromString<CursedItemFile>(raw).items.map {
            CursedItem(
                key = it.key,
                nameDe = it.nameDe,
                nameEn = it.nameEn,
                points = it.points,
                pointsTwoPlayer = it.pointsTwoPlayer,
            )
        }
    }

    private companion object {
        const val ASSET_PATH = "cards/cursed_items.json"
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class CursedItemFile(
    val version: Int = 1,
    val items: List<CursedItemDto> = emptyList(),
)

@Serializable
private data class CursedItemDto(
    val key: String,
    val nameDe: String,
    val nameEn: String,
    val points: Int,
    val pointsTwoPlayer: Int? = null,
)
