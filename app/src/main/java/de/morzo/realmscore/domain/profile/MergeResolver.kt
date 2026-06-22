package de.morzo.realmscore.domain.profile

/**
 * Pure, Android-freie Auflösung von Profil-Merge-Zeigern (Phase 2/3 Profil-Rework).
 *
 * Ein Profil kann non-destruktiv in ein anderes verschmolzen sein (`mergeTargetId`). Für Statistiken
 * & Auswahl muss zur Laufzeit das **kanonische** Profil bestimmt werden, in das eine Id letztlich
 * zeigt. Defensiv gegen drei Randfälle (vgl. Annahmen im HANDOFF):
 *  - **Archiviert**: ist die Start-Id oder ein Knoten der Kette archiviert, zählt die Lineage nirgends
 *    → `null` (ausgeschlossen).
 *  - **Dangling Target**: zeigt ein Knoten auf eine lokal unbekannte Id (Import/P2P), endet die
 *    Auflösung am letzten bekannten Knoten.
 *  - **Zyklus / zu lange Kette**: bricht stabil ab (sollte durch die Schreib-Guards nie entstehen).
 */
object MergeResolver {

    const val MAX_DEPTH = 32

    /**
     * Liefert die kanonische (End-)Id für [id] oder `null`, wenn die Lineage ausgeschlossen ist
     * (archiviert oder unbekannte Start-Id).
     */
    fun resolveCanonical(
        id: String,
        mergeTargetOf: (String) -> String?,
        isArchived: (String) -> Boolean,
        isKnown: (String) -> Boolean,
    ): String? {
        if (!isKnown(id)) return null
        var cursor = id
        val visited = HashSet<String>()
        var depth = 0
        while (true) {
            if (isArchived(cursor)) return null
            if (!visited.add(cursor)) return cursor // Zyklus → stabil abbrechen
            val target = mergeTargetOf(cursor) ?: return cursor
            if (!isKnown(target)) return cursor // dangling → am letzten bekannten Knoten enden
            cursor = target
            if (++depth > MAX_DEPTH) return cursor
        }
    }
}
