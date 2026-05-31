package de.morzo.realmscore.domain.scoring

import de.morzo.realmscore.domain.model.Suit

data class JokerAssignment(
    val jokerKey: String,
    val targetCardKey: String?,
    val targetSuit: Suit? = null,
)
