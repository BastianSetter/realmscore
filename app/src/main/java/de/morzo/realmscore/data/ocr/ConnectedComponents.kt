package de.morzo.realmscore.data.ocr

import android.graphics.Rect

/** Shared blob labelling for the scan detectors (Phase 26). */
internal object ConnectedComponents {

    /** Iterative flood-fill (4-connectivity) → one bounding box per connected `true` region. */
    fun boxes(mask: BooleanArray, w: Int, h: Int): List<Rect> {
        val visited = BooleanArray(mask.size)
        val result = ArrayList<Rect>()
        val stack = IntArray(mask.size)
        for (seed in mask.indices) {
            if (!mask[seed] || visited[seed]) continue
            var sp = 0
            stack[sp++] = seed
            visited[seed] = true
            var minX = w; var minY = h; var maxX = 0; var maxY = 0
            while (sp > 0) {
                val idx = stack[--sp]
                val x = idx % w
                val y = idx / w
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                if (x > 0 && mask[idx - 1] && !visited[idx - 1]) { visited[idx - 1] = true; stack[sp++] = idx - 1 }
                if (x < w - 1 && mask[idx + 1] && !visited[idx + 1]) { visited[idx + 1] = true; stack[sp++] = idx + 1 }
                if (y > 0 && mask[idx - w] && !visited[idx - w]) { visited[idx - w] = true; stack[sp++] = idx - w }
                if (y < h - 1 && mask[idx + w] && !visited[idx + w]) { visited[idx + w] = true; stack[sp++] = idx + w }
            }
            result += Rect(minX, minY, maxX + 1, maxY + 1)
        }
        return result
    }
}
