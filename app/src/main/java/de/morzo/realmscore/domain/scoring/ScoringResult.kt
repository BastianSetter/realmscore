package de.morzo.realmscore.domain.scoring

data class ScoringResult(
    val totalScore: Int,
    val perCard: List<CardScoreResult>,
    val blankedKeys: Set<String>,
)

data class CardScoreResult(
    val cardKey: String,
    val effectiveName: String,
    val contributedScore: Int,
    val isBlanked: Boolean,
    val effects: List<EffectApplication>,
)
