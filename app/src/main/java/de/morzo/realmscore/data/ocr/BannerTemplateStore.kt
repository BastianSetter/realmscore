package de.morzo.realmscore.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Loads and stores the Phase-29 banner templates. A template is a canonical grayscale Standardbox PNG
 * named `<cardKey>.png`. Two sources, **filesDir overrides win over assets** so the Scanner-Test
 * screen can add/replace templates without a rebuild:
 *  - bundled: `assets/`[ASSET_DIR]`/<cardKey>.png`
 *  - runtime: `filesDir/`[TEMPLATES_DIR]`/<cardKey>.png` (exported from the Scanner-Test screen)
 *
 * Missing templates are allowed — a card without one is simply not matchable by bitmap. The card set
 * is open (Phase 30 → 100 cards); nothing here hard-codes a count. German edition only for now; an
 * English set would live under a sibling [ASSET_DIR] constant.
 */
class BannerTemplateStore(private val context: Context) {

    private val overrideDir: File get() = File(context.filesDir, TEMPLATES_DIR)

    /** All card keys that have a template (filesDir ∪ assets), for the inventory count / miss list. */
    fun availableKeys(): Set<String> = assetKeys() + overrideKeys()

    /** Loads every available template as a [BannerTemplate] (filesDir overrides assets by key). */
    fun load(): List<BannerTemplate> {
        val keys = availableKeys()
        return keys.mapNotNull { key ->
            val bitmap = loadBitmap(key) ?: return@mapNotNull null
            val (gray, _) = BannerNormalizer.grayOf(bitmap)
            BannerTemplate(key, gray)
        }
    }

    /** Writes [box] (any size — scaled to canonical grayscale) as the filesDir override for [cardKey]. */
    fun save(cardKey: String, box: Bitmap) {
        val (_, canonical) = BannerNormalizer.grayOf(box)
        overrideDir.mkdirs()
        FileOutputStream(File(overrideDir, "$cardKey.png")).use { out ->
            canonical.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    /**
     * Zips all filesDir override templates into `cacheDir/`[EXPORT_DIR]`/scan_templates.zip` and
     * returns the file (for a share-sheet), or null if there are no overrides to export. The user
     * unpacks it once into `app/src/main/assets/`[ASSET_DIR]`/` to bake the templates into the app.
     */
    fun exportOverridesZip(): File? {
        val files = overrideDir.listFiles { f -> f.extension.equals("png", ignoreCase = true) }
            ?.sortedBy { it.name } ?: return null
        if (files.isEmpty()) return null
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        val zip = File(dir, "scan_templates.zip")
        ZipOutputStream(FileOutputStream(zip)).use { out ->
            for (f in files) {
                out.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        return zip
    }

    private fun loadBitmap(cardKey: String): Bitmap? {
        val override = File(overrideDir, "$cardKey.png")
        if (override.exists()) BitmapFactory.decodeFile(override.absolutePath)?.let { return it }
        return runCatching {
            context.assets.open("$ASSET_DIR/$cardKey.png").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    private fun assetKeys(): Set<String> = runCatching {
        context.assets.list(ASSET_DIR)?.filter { it.endsWith(".png") }?.map { it.removeSuffix(".png") }?.toSet()
    }.getOrNull().orEmpty()

    private fun overrideKeys(): Set<String> =
        overrideDir.listFiles { f -> f.extension.equals("png", ignoreCase = true) }
            ?.map { it.nameWithoutExtension }?.toSet().orEmpty()

    companion object {
        /** Bundled templates (German edition). An English set would use a sibling dir. */
        const val ASSET_DIR = "scan_templates"
        private const val TEMPLATES_DIR = "scan_templates"
        private const val EXPORT_DIR = "template_exports"
    }
}
