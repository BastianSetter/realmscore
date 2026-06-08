package de.morzo.realmscore.data.repository

import de.morzo.realmscore.domain.model.ProfileColors

internal object ProfilePalette {
    val COLORS: List<Int> = ProfileColors.PALETTE

    fun pickNextColor(usedColors: List<Int>): Int {
        if (COLORS.isEmpty()) return 0xFF6750A4.toInt()
        val counts = IntArray(COLORS.size)
        usedColors.forEach { color ->
            val idx = COLORS.indexOf(color)
            if (idx >= 0) counts[idx]++
        }
        val minCount = counts.min()
        val firstFreeIndex = counts.indexOfFirst { it == minCount }
        return COLORS[firstFreeIndex]
    }
}
