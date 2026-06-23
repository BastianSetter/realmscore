package de.morzo.realmscore.data.ocr

import android.content.Context
import de.morzo.realmscore.data.cards.CardLookup

/**
 * Flavour seam (Phase 26): the `fdroid` flavour builds the Tesseract-based scanner. `AppContainer`
 * (in `main`) calls this; the active flavour's implementation is the one compiled in.
 */
object ScannerFactory {
    fun create(context: Context, cardLookup: CardLookup): CardScanner =
        TesseractCardScanner(TesseractManager(context), CardNameMatcher(cardLookup))
}
