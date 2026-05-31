package de.morzo.realmscore.domain.scoring

sealed class HandCondition {
    abstract fun evaluate(hand: List<ResolvedCard>, self: ResolvedCard): Boolean

    data class Contains(
        val matcher: CardMatcher,
        val minCount: Int = 1,
        val excludeSelf: Boolean = true,
    ) : HandCondition() {
        override fun evaluate(hand: List<ResolvedCard>, self: ResolvedCard): Boolean {
            val pool = if (excludeSelf) hand.filter { it.originalKey != self.originalKey } else hand
            return pool.count { matcher.matches(it) } >= minCount
        }
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
    }

    data class Or(val parts: List<HandCondition>) : HandCondition() {
        constructor(vararg parts: HandCondition) : this(parts.toList())

        override fun evaluate(hand: List<ResolvedCard>, self: ResolvedCard): Boolean =
            parts.any { it.evaluate(hand, self) }
    }
}
