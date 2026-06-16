package de.morzo.realmscore.data.ocr

import android.content.Context
import de.morzo.realmscore.data.cards.CardLookup

/**
 * Flavour seam (Phase 26): the `play` flavour builds the ML Kit-based scanner. `AppContainer` (in
 * `main`) calls this; the active flavour's implementation is the one compiled in. The [context] is
 * unused here (ML Kit needs none) but kept so both flavours share one factory signature.
 */
object ScannerFactory {
    fun create(context: Context, cardLookup: CardLookup): CardScanner =
        MlKitCardScanner(CardNameMatcher(cardLookup))
}
