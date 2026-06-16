package de.morzo.realmscore.data.ocr

import de.morzo.realmscore.data.cards.CardLookup

/**
 * Fuzzy-matches OCR text against every card's German *and* English name (Phase 26). Shared by both
 * flavours' scanners. Per the spec there is no confidence threshold and no ambiguity UI — the best
 * match wins; [MIN_MATCH_SCORE] only rejects pure garbage so the slot stays empty for manual entry.
 */
class CardNameMatcher(private val cardLookup: CardLookup) {

    /** Top fuzzy-match candidates for an OCR line, highest score first. */
    fun scoredCandidates(ocrText: String): List<CandidateScore> {
        val needle = StringDistance.normalize(ocrText)
        if (needle.length < MIN_OCR_LENGTH) return emptyList()
        return cardLookup.getAll()
            .map { card ->
                // Full ratio (not partial): a card name *embedded* in a rule line ("…mit Regensturm…")
                // must NOT score 100%. The value digits are already dropped by normalize, so the
                // value-circle prefix doesn't need partial matching.
                val score = listOfNotNull(card.nameDe, card.nameEn)
                    .maxOf { StringDistance.similarity(needle, StringDistance.normalize(it)) }
                CandidateScore(card, score)
            }
            .sortedByDescending { it.score }
    }
}
