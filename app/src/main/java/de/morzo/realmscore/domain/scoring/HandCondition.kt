package de.morzo.realmscore.domain.scoring

sealed class HandCondition {
    abstract fun evaluate(hand: List<ResolvedCard>, self: ResolvedCard): Boolean

    /**
     * The hand cards that make this condition true — i.e. the cards a satisfied bonus depends on.
     * Used by the ring visualization (Phase 18) to draw influence lines. Absence conditions
     * (NotContains) contribute nothing; only the satisfied branches of an Or are included.
     */
    open fun contributingKeys(hand: List<ResolvedCard>, self: ResolvedCard): List<String> = emptyList()

    data class Contains(
        val matcher: CardMatcher,
        val minCount: Int = 1,
        val excludeSelf: Boolean = true,
    ) : HandCondition() {
        private fun pool(hand: List<ResolvedCard>, self: ResolvedCard) =
            if (excludeSelf) hand.filter { it.originalKey != self.originalKey } else hand

        override fun evaluate(hand: List<ResolvedCard>, self: ResolvedCard): Boolean =
            pool(hand, self).count { matcher.matches(it) } >= minCount

        override fun contributingKeys(hand: List<ResolvedCard>, self: ResolvedCard): List<String> =
            pool(hand, self).filter { matcher.matches(it) }.map { it.originalKey }
    }

    data class NotContains(
        val matcher: CardMatcher,
        val excludeSelf: Boolean = true,
    ) : HandCondition() {
        override fun evaluate(hand: List<ResolvedCard>, self: ResolvedCard): Boolean {
            val pool = if (excludeSelf) hand.filter { it.originalKey != self.originalKey } else hand
            return pool.none { matcher.matches(it) }
        }
    }

    data class And(val parts: List<HandCondition>) : HandCondition() {
        constructor(vararg parts: HandCondition) : this(parts.toList())

        override fun evaluate(hand: List<ResolvedCard>, self: ResolvedCard): Boolean =
            parts.all { it.evaluate(hand, self) }

        override fun contributingKeys(hand: List<ResolvedCard>, self: ResolvedCard): List<String> =
            parts.flatMap { it.contributingKeys(hand, self) }
    }

    data class Or(val parts: List<HandCondition>) : HandCondition() {
        constructor(vararg parts: HandCondition) : this(parts.toList())

        override fun evaluate(hand: List<ResolvedCard>, self: ResolvedCard): Boolean =
            parts.any { it.evaluate(hand, self) }

        override fun contributingKeys(hand: List<ResolvedCard>, self: ResolvedCard): List<String> =
            parts.filter { it.evaluate(hand, self) }.flatMap { it.contributingKeys(hand, self) }
    }
}
