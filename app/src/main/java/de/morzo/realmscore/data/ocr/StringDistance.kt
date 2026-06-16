package de.morzo.realmscore.data.ocr

import kotlin.math.max

/**
 * Lightweight, dependency-free string similarity used to fuzzy-match OCR output against the known
 * card names (Phase 26). No external library so the F-Droid build stays clean.
 */
object StringDistance {

    /** Levenshtein edit distance between two strings. */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,      // insertion
                    prev[j] + 1,          // deletion
                    prev[j - 1] + cost,   // substitution
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }

    /**
     * Similarity in [0, 1]: 1.0 = identical, 0.0 = completely different. Derived from the normalized
     * Levenshtein distance over the longer string.
     */
    fun similarity(a: String, b: String): Float {
        if (a.isEmpty() && b.isEmpty()) return 1f
        val longer = max(a.length, b.length)
        if (longer == 0) return 1f
        return 1f - levenshtein(a, b).toFloat() / longer
    }

    /**
     * Best of the full similarity and a *partial* ratio (FuzzyWuzzy-style): the shorter string vs the
     * best-matching equal-length window of the longer one. This stops an extra OCR token — e.g. the
     * value-circle letter glued to the name — from tanking an otherwise good match.
     */
    fun bestRatio(a: String, b: String): Float {
        val full = similarity(a, b)
        val (short, long) = if (a.length <= b.length) a to b else b to a
        if (short.isEmpty() || long.length == short.length) return full
        var best = full
        for (i in 0..(long.length - short.length)) {
            val window = long.substring(i, i + short.length)
            val s = similarity(short, window)
            if (s > best) best = s
        }
        return best
    }

    /**
     * Normalizes a string for matching: lowercase, strip everything that is not a letter/digit, and
     * fold common German diacritics so OCR slips (ä→a, ß→ss, …) still match.
     */
    fun normalize(text: String): String {
        val lowered = text.lowercase().trim()
        val sb = StringBuilder(lowered.length)
        for (ch in lowered) {
            when (ch) {
                'ä' -> sb.append("ae")
                'ö' -> sb.append("oe")
                'ü' -> sb.append("ue")
                'ß' -> sb.append("ss")
                // Letters only: card names carry no digits, so dropping them removes OCR noise from
                // the value number / "35/53" index that can sit inside a crop.
                else -> if (ch.isLetter()) sb.append(ch)
            }
        }
        return sb.toString()
    }
}
