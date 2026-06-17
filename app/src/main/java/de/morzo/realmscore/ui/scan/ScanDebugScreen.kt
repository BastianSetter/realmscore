package de.morzo.realmscore.ui.scan

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.data.ocr.CardScanner
import de.morzo.realmscore.data.ocr.ScanImageOps
import de.morzo.realmscore.data.ocr.ScanRegion
import de.morzo.realmscore.data.ocr.ScanStage
import kotlin.math.roundToInt

/**
 * Developer tool (debug builds only) to inspect the camera-scan pipeline (Phase 26): pick a photo,
 * run the detailed recognizer, and see how many card rectangles were found, the exact crop fed to
 * OCR, the raw OCR text + confidence, and the top fuzzy-match candidates with scores. This is the
 * surface for tuning detection/crop/binarization against real photos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanDebugScreen(
    scanner: CardScanner,
    onBack: () -> Unit,
) {
    val vm: ScanDebugViewModel = viewModel(factory = ScanDebugViewModel.Factory(scanner))
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val brightFraction = remember { mutableFloatStateOf(ScanImageOps.whiteTextBrightFraction) }
    val titlePad = remember { mutableFloatStateOf(ScanImageOps.titlePadFraction) }
    val redGate = remember { mutableFloatStateOf(ScanImageOps.titleRowRedFraction) }
    val whiteGate = remember { mutableFloatStateOf(ScanImageOps.titleRowWhiteFraction) }

    fun loadBitmap(uri: Uri): Bitmap? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val largest = maxOf(info.size.width, info.size.height)
            val cap = 2200
            if (largest > cap) {
                val s = cap.toFloat() / largest
                decoder.setTargetSize(
                    (info.size.width * s).roundToInt().coerceAtLeast(1),
                    (info.size.height * s).roundToInt().coerceAtLeast(1),
                )
            }
        }
    }.getOrNull()

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) loadBitmap(uri)?.let { vm.analyze(it, 0, SINGLE_CARD) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            vm.analyze(bitmap.copy(Bitmap.Config.ARGB_8888, false), 0, SINGLE_CARD)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scanner-Test (Debug)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                Text(
                    "Einzelkarten-Diagnose (Tesseract): eine aufrechte Karte, die den Rahmen füllt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    "Rot-Gate (Bannerstart): ${"%.2f".format(redGate.floatValue)} " +
                        "(Rot-Anteil/Zeile)",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = redGate.floatValue,
                    onValueChange = {
                        redGate.floatValue = it
                        ScanImageOps.titleRowRedFraction = it
                    },
                    onValueChangeFinished = { vm.reanalyze() },
                    valueRange = 0.02f..0.50f,
                )
            }
            item {
                Text(
                    "Weiß-Gate (Textkanten): ${"%.2f".format(whiteGate.floatValue)} " +
                        "(Weiß-Anteil/Zeile)",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = whiteGate.floatValue,
                    onValueChange = {
                        whiteGate.floatValue = it
                        ScanImageOps.titleRowWhiteFraction = it
                    },
                    onValueChangeFinished = { vm.reanalyze() },
                    valueRange = 0.01f..0.40f,
                )
            }
            item {
                Text(
                    "Weiß-Helligkeit: ${"%.2f".format(brightFraction.floatValue)} " +
                        "(höher = dünner, niedriger = fetter)",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = brightFraction.floatValue,
                    onValueChange = {
                        brightFraction.floatValue = it
                        ScanImageOps.whiteTextBrightFraction = it
                    },
                    onValueChangeFinished = { vm.reanalyze() },
                    valueRange = 0.30f..0.90f,
                )
            }
            item {
                Text(
                    "Rand um Titel: ${"%.2f".format(titlePad.floatValue)} " +
                        "(Anteil der Titelhöhe oben+unten)",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = titlePad.floatValue,
                    onValueChange = {
                        titlePad.floatValue = it
                        ScanImageOps.titlePadFraction = it
                    },
                    onValueChangeFinished = { vm.reanalyze() },
                    valueRange = 0.0f..1.5f,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                    ) { Text("Aus Galerie") }
                    OutlinedButton(
                        onClick = { cameraLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Kamera (klein)") }
                }
            }
            item {
                Text(
                    "Galerie = volle Auflösung (empfohlen). Kamera-Vorschau liefert nur ein kleines Bild.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.isProcessing) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                }
            }

            state.sourcePreview?.let { preview ->
                item {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            state.report?.let { report ->
                item {
                    Text(
                        "Modus: ${report.mode}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                if (report.stages.isNotEmpty()) {
                    item {
                        Text("Pipeline – Schritt für Schritt", style = MaterialTheme.typography.titleSmall)
                    }
                    itemsIndexed(report.stages) { index, stage ->
                        StageCard(index = index, stage = stage)
                    }
                }
                item {
                    Text("Ergebnis", style = MaterialTheme.typography.titleSmall)
                }
                itemsIndexed(report.regions) { index, region ->
                    RegionCard(index = index, region = region)
                }
            }
        }
    }
}

/** maxCards is ignored by the Tesseract single-card path; pass a fixed 1 for clarity. */
private const val SINGLE_CARD = 1

@Composable
private fun StageCard(index: Int, stage: ScanStage) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "${index + 1}. ${stage.label}",
                style = MaterialTheme.typography.labelLarge,
            )
            Image(
                bitmap = stage.image.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .background(Color(0xFFEDEDED)),
                contentScale = ContentScale.Fit,
            )
            if (stage.note.isNotEmpty()) {
                Text(
                    stage.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RegionCard(index: Int, region: ScanRegion) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Region ${index + 1} · Band: ${region.band} · Konfidenz: ${region.confidence}",
                style = MaterialTheme.typography.labelLarge,
            )
            // The exact (binarized) crop handed to Tesseract.
            Image(
                bitmap = region.crop.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color.White),
                contentScale = ContentScale.Fit,
            )
            Text("OCR: \"${region.ocrText}\"", style = MaterialTheme.typography.bodyMedium)
            if (region.candidates.isEmpty()) {
                Text(
                    "Keine Übereinstimmung",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                region.candidates.forEachIndexed { i, c ->
                    val pct = (c.score * 100).roundToInt()
                    Text(
                        "${i + 1}. ${c.card.nameDe} — $pct%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (i == 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
