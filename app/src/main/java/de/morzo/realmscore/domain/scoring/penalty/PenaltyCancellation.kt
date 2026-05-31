package de.morzo.realmscore.domain.scoring.penalty

import de.morzo.realmscore.domain.model.Suit

/**
 * A penalty modifier emitted by one card to soften another card's penalty.
 *
 * sourceKey: which card emits the cancellation (so we can drop it if its source is blanked).
 */
sealed class PenaltyCancellation {
    abstract val sourceKey: String

    /** Rune des Schutzes — removes every penalty from every card. */
    data class CancelAll(override val sourceKey: String) : PenaltyCancellation()

    /**
     * Höhle → Weather, Gebirge → Flood, Herr der Bestien → Beast.
     * Removes penalty wholesale from every card whose effective suit is in [targetSuits].
     */
    data class CancelBySuit(
        override val sourceKey: String,
        val targetSuits: Set<Suit>,
    ) : PenaltyCancellation()

    /**
     * Insel — player picks a single Flood/Flame whose penalty is cancelled.
     * targetCardKey is null when no choice is set.
     */
    data class CancelOneOf(
        override val sourceKey: String,
        val targetCardKey: String?,
    ) : PenaltyCancellation()

    /**
     * Waldläufer ("Army" word removed from every penalty),
     * Kriegsschiff ("Army" word removed from every Flood penalty).
     *
     * strippedSuits: suits that should be ignored when penalty-rule counts cards.
     * scopeSuits = null → applies to all cards; non-null → only cards whose effective
     *   suit is in scopeSuits feel the strip.
     */
    data class StripSuitWord(
        override val sourceKey: String,
        val strippedSuits: Set<Suit>,
        val scopeSuits: Set<Suit>? = null,
    ) : PenaltyCancellation()
}
