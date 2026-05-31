package de.morzo.realmscore.data.repository

internal object ProfilePalette {
    val COLORS: List<Int> = listOf(
        0xFF6750A4.toInt(),
        0xFF006E1C.toInt(),
        0xFFB3261E.toInt(),
        0xFF1A73E8.toInt(),
        0xFFEF6C00.toInt(),
        0xFF00838F.toInt(),
        0xFF8E24AA.toInt(),
        0xFF558B2F.toInt(),
    )

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
