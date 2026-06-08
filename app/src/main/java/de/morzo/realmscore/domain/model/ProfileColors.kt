package de.morzo.realmscore.domain.model

/**
 * Die feste 8er-Farbpalette für Profile. Einzige Quelle der Wahrheit –
 * von der automatischen Farbvergabe (NewGame) und vom ColorPicker (Phase 17) genutzt.
 */
object ProfileColors {
    val PALETTE: List<Int> = listOf(
        0xFF6750A4.toInt(),
        0xFF006E1C.toInt(),
        0xFFB3261E.toInt(),
        0xFF1A73E8.toInt(),
        0xFFEF6C00.toInt(),
        0xFF00838F.toInt(),
        0xFF8E24AA.toInt(),
        0xFF558B2F.toInt(),
    )
}
