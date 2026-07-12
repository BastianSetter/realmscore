package de.morzo.realmscore.ui.scan

import android.content.Intent
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.data.ocr.BannerNormalizer
import de.morzo.realmscore.data.ocr.BannerTemplateStore
import de.morzo.realmscore.data.ocr.CardScanner
import de.morzo.realmscore.data.ocr.ScanImageOps
import de.morzo.realmscore.data.ocr.ScanRegion
import de.morzo.realmscore.data.ocr.ScanStage
import de.morzo.realmscore.data.ocr.TemplateCardScanner
import de.morzo.realmscore.domain.model.CardDefinition
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.roundToInt

/**
 * Developer tool (debug builds only) to inspect the camera-scan pipeline (Phase 26 + 29): pick a
 * photo and run the detailed recognizer via either OCR or the bitmap-template path (A/B toggle on the
 * same photo). Shows every intermediate stage, the crop, the top fuzzy/NCC candidates with scores,
 * and — for the bitmap path — the template inventory, per-region "save as template", and export.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanDebugScreen(
    ocrScanner: CardScanner,
    templateScanner: TemplateCardScanner,
    templateStore: BannerTemplateStore,
    cardLookup: CardLookup,
    onBack: () -> Unit,
) {
    val vm: ScanDebugViewModel = viewModel(
        factory = ScanDebugViewModel.Factory(ocrScanner, templateScanner, templateStore, cardLookup),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }

    // OCR knobs.
    val brightFraction = remember { mutableFloatStateOf(ScanImageOps.whiteTextBrightFraction) }
    val padTop = remember { mutableFloatStateOf(ScanImageOps.titlePadTopFraction) }
    val padBottom = remember { mutableFloatStateOf(ScanImageOps.titlePadBottomFraction) }
    val sideRed = remember { mutableFloatStateOf(ScanImageOps.titleSideRed) }
    val redBorder = remember { mutableFloatStateOf(ScanImageOps.titleBorderRed) }
    val whiteMin = remember { mutableFloatStateOf(ScanImageOps.titleBorderWhite) }
    val whiteMax = remember { mutableFloatStateOf(ScanImageOps.titleTextWhite) }
    // Bitmap (Phase 29) knobs.
    val redGreenFactor = remember { mutableFloatStateOf(ScanImageOps.redMinGreenFactor) }
    val redBlueFactor = remember { mutableFloatStateOf(ScanImageOps.redMinBlueFactor) }
    val redMaxBlue = remember { mutableFloatStateOf(ScanImageOps.redMaxBlueFactor) }
    val rawMargin = remember { mutableFloatStateOf(BannerNormalizer.RAW_MARGIN) }
    val rawMarginTop = remember { mutableFloatStateOf(BannerNormalizer.RAW_MARGIN_TOP) }
    val probeX1 = remember { mutableFloatStateOf(BannerNormalizer.PROBE_X1) }
    val probeX2 = remember { mutableFloatStateOf(BannerNormalizer.PROBE_X2) }
    val rightScanStart = remember { mutableFloatStateOf(BannerNormalizer.RIGHT_SCAN_START) }
    val descend = remember { mutableFloatStateOf(BannerNormalizer.DESCEND) }
    val factorA = remember { mutableFloatStateOf(BannerNormalizer.BOX_FACTOR_A) }
    val factorB = remember { mutableFloatStateOf(BannerNormalizer.BOX_FACTOR_B) }
    val redRatioTop = remember { mutableFloatStateOf(BannerNormalizer.redMinRatioTop) }
    val redRatioRight = remember { mutableFloatStateOf(BannerNormalizer.redMinRatioRight) }
    val goldRed = remember { mutableFloatStateOf(BannerNormalizer.goldMinRed.toFloat()) }
    val goldBlueGreen = remember { mutableFloatStateOf(BannerNormalizer.goldMaxBlueGreenRatio) }
    val minTemplateScore = remember { mutableFloatStateOf(BannerNormalizer.MIN_TEMPLATE_SCORE) }

    val maxCards = remember { mutableIntStateOf(FAN_HAND_CARDS) }
    // Card assigned to each region (by region index) before a single batch save. Which region's
    // picker dialog is currently open (region index), if any.
    val assignments = remember { mutableStateMapOf<Int, CardDefinition>() }
    var pickTarget by remember { mutableStateOf<Int?>(null) }

    // A fresh analysis (new photo or re-run) invalidates the old region indices → drop assignments.
    LaunchedEffect(state.report) { assignments.clear() }

    // Share-sheet / snackbar side effects.
    LaunchedEffect(Unit) {
        vm.events.collectLatest { event ->
            when (event) {
                is ScanDebugEvent.Message -> snackbarHost.showSnackbar(event.text)
                is ScanDebugEvent.ShareZip -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Vorlagen exportieren"))
                }
            }
        }
    }

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
        if (uri != null) loadBitmap(uri)?.let { vm.analyze(it, 0, maxCards.intValue) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            vm.analyze(bitmap.copy(Bitmap.Config.ARGB_8888, false), 0, maxCards.intValue)
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
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            // A/B: OCR vs. bitmap template matching on the same photo.
            item {
                Text("Pfad (A/B)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.mode == ScanMode.OCR,
                        onClick = { vm.setMode(ScanMode.OCR) },
                        label = { Text("OCR") },
                    )
                    FilterChip(
                        selected = state.mode == ScanMode.BITMAP,
                        onClick = { vm.setMode(ScanMode.BITMAP) },
                        label = { Text("Bitmap") },
                    )
                }
            }

            if (state.mode == ScanMode.BITMAP) {
                item {
                    Text(
                        "Vorlagen: ${state.inventory.available}/${state.inventory.total}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    OutlinedButton(onClick = { vm.exportTemplates(context) }) {
                        Text("Alle exportieren (ZIP)")
                    }
                    if (state.inventory.missing.isNotEmpty()) {
                        Text(
                            "Fehlt (${state.inventory.missing.size}): " +
                                state.inventory.missing.take(12).joinToString { it.nameDe } +
                                if (state.inventory.missing.size > 12) " …" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Text(
                    "Kartenzahl (Layout): ${maxCards.intValue} " +
                        "(${if (maxCards.intValue > FAN_HAND_CARDS) "2 Spalten" else "1 Spalte"})",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FAN_CARD_CHOICES.forEach { choice ->
                        FilterChip(
                            selected = maxCards.intValue == choice,
                            onClick = {
                                if (maxCards.intValue != choice) {
                                    maxCards.intValue = choice
                                    vm.reanalyze(choice)
                                }
                            },
                            label = { Text("$choice") },
                        )
                    }
                }
            }

            if (state.mode == ScanMode.BITMAP) {
                bitmapSliders(redGreenFactor, redBlueFactor, redMaxBlue, rawMargin, rawMarginTop, probeX1, probeX2, rightScanStart, descend, factorA, factorB, redRatioTop, redRatioRight, goldRed, goldBlueGreen, minTemplateScore, vm)
            } else {
                ocrSliders(redBorder, whiteMin, whiteMax, brightFraction, padTop, padBottom, sideRed, vm)
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
                    Text("Modus: ${report.mode}", style = MaterialTheme.typography.titleSmall)
                    if (report.durationMs > 0) {
                        Text(
                            "Matching-Dauer: ${report.durationMs} ms" +
                                if (report.regionCount > 0) " (~${report.durationMs / report.regionCount} ms/Banner)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                    RegionCard(
                        index = index,
                        region = region,
                        canAssign = state.mode == ScanMode.BITMAP && region.band == "Bitmap",
                        assignedName = assignments[index]?.nameDe,
                        onAssign = { pickTarget = index },
                    )
                }
                if (state.mode == ScanMode.BITMAP && report.regions.any { it.band == "Bitmap" }) {
                    item {
                        Button(
                            onClick = {
                                val entries = assignments.mapNotNull { (idx, card) ->
                                    report.regions.getOrNull(idx)?.takeIf { it.band == "Bitmap" }?.let { card.key to it.crop }
                                }
                                vm.saveTemplates(entries)
                                assignments.clear()
                            },
                            enabled = assignments.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("${assignments.size} Vorlage(n) speichern") }
                    }
                }
            }
        }
    }

    pickTarget?.let { index ->
        val region = state.report?.regions?.getOrNull(index)
        if (region == null) {
            pickTarget = null
        } else {
            SaveTemplateDialog(
                defaultQuery = assignments[index]?.nameDe
                    ?: region.candidates.firstOrNull()?.card?.nameDe.orEmpty(),
                search = vm::searchCards,
                onPick = { card ->
                    assignments[index] = card
                    pickTarget = null
                },
                onDismiss = { pickTarget = null },
            )
        }
    }
}

/** A normal Fantasy Realms hand is 7 cards (one fan stack); the default in the debug screen. */
private const val FAN_HAND_CARDS = 7

/** Selectable card counts: 7 = a hand (one stack), 10/12 = the Mittelfeld (two side-by-side stacks). */
private val FAN_CARD_CHOICES = listOf(7, 10, 12)

/** Phase-29 tuning sliders (probe lines, safe gap, gold thresholds, match floor). */
private fun androidx.compose.foundation.lazy.LazyListScope.bitmapSliders(
    redGreenFactor: androidx.compose.runtime.MutableFloatState,
    redBlueFactor: androidx.compose.runtime.MutableFloatState,
    redMaxBlue: androidx.compose.runtime.MutableFloatState,
    rawMargin: androidx.compose.runtime.MutableFloatState,
    rawMarginTop: androidx.compose.runtime.MutableFloatState,
    probeX1: androidx.compose.runtime.MutableFloatState,
    probeX2: androidx.compose.runtime.MutableFloatState,
    rightScanStart: androidx.compose.runtime.MutableFloatState,
    descend: androidx.compose.runtime.MutableFloatState,
    factorA: androidx.compose.runtime.MutableFloatState,
    factorB: androidx.compose.runtime.MutableFloatState,
    redRatioTop: androidx.compose.runtime.MutableFloatState,
    redRatioRight: androidx.compose.runtime.MutableFloatState,
    goldRed: androidx.compose.runtime.MutableFloatState,
    goldBlueGreen: androidx.compose.runtime.MutableFloatState,
    minTemplateScore: androidx.compose.runtime.MutableFloatState,
    vm: ScanDebugViewModel,
) {
    item {
        SliderRow("Blob-Rot r ≥ ·g (Rot>Grün)", redGreenFactor.floatValue, 1.0f..1.6f, { redGreenFactor.floatValue = it; ScanImageOps.redMinGreenFactor = it }, vm)
    }
    item {
        SliderRow("Blob-Rot b ≥ ·g (kein Holz)", redBlueFactor.floatValue, 0.5f..1.1f, { redBlueFactor.floatValue = it; ScanImageOps.redMinBlueFactor = it }, vm)
    }
    item {
        SliderRow("Blob-Rot b ≤ ·r (kein Blau)", redMaxBlue.floatValue, 0.6f..1.0f, { redMaxBlue.floatValue = it; ScanImageOps.redMaxBlueFactor = it }, vm)
    }
    item {
        SliderRow("Roh-Rand (·Blobhöhe)", rawMargin.floatValue, 0.0f..0.30f, { rawMargin.floatValue = it; BannerNormalizer.RAW_MARGIN = it }, vm)
    }
    item {
        SliderRow("Roh-Rand oben (·Blobhöhe)", rawMarginTop.floatValue, 0.0f..0.20f, { rawMarginTop.floatValue = it; BannerNormalizer.RAW_MARGIN_TOP = it }, vm)
    }
    item {
        SliderRow("Probelinie 1 (%)", probeX1.floatValue, 0.20f..0.70f, { probeX1.floatValue = it; BannerNormalizer.PROBE_X1 = it }, vm)
    }
    item {
        SliderRow("Probelinie 2 (%)", probeX2.floatValue, 0.55f..0.95f, { probeX2.floatValue = it; BannerNormalizer.PROBE_X2 = it }, vm)
    }
    item {
        SliderRow("Bandende-Suche Start (%)", rightScanStart.floatValue, 0.55f..0.98f, { rightScanStart.floatValue = it; BannerNormalizer.RIGHT_SCAN_START = it }, vm)
    }
    item {
        SliderRow("Scan-Abstieg (·Blobhöhe)", descend.floatValue, 0.0f..0.30f, { descend.floatValue = it; BannerNormalizer.DESCEND = it }, vm)
    }
    item {
        SliderRow("Box unten factorA (·Breite)", factorA.floatValue, 0.10f..1.50f, { factorA.floatValue = it; BannerNormalizer.BOX_FACTOR_A = it }, vm)
    }
    item {
        SliderRow("Box links factorB (·Breite)", factorB.floatValue, 0.0f..1.0f, { factorB.floatValue = it; BannerNormalizer.BOX_FACTOR_B = it }, vm)
    }
    item {
        SliderRow("Rot-Gate oben 2r/(g+b) ≥", redRatioTop.floatValue, 1.0f..6.0f, { redRatioTop.floatValue = it; BannerNormalizer.redMinRatioTop = it }, vm)
    }
    item {
        SliderRow("Rot-Gate rechts 2r/(g+b) ≥", redRatioRight.floatValue, 1.0f..6.0f, { redRatioRight.floatValue = it; BannerNormalizer.redMinRatioRight = it }, vm)
    }
    item {
        SliderRow("Gold Min-Rot", goldRed.floatValue, 60f..220f, { goldRed.floatValue = it; BannerNormalizer.goldMinRed = it.toInt() }, vm, "%.0f")
    }
    item {
        SliderRow("Gold Blau/Grün ≤ (b/g)", goldBlueGreen.floatValue, 0.10f..1.0f, { goldBlueGreen.floatValue = it; BannerNormalizer.goldMaxBlueGreenRatio = it }, vm)
    }
    item {
        SliderRow("Match-Schwelle (NCC)", minTemplateScore.floatValue, 0.20f..0.90f, { minTemplateScore.floatValue = it; BannerNormalizer.MIN_TEMPLATE_SCORE = it }, vm)
    }
}

/** Existing OCR tuning sliders (Phase 26), unchanged behaviour. */
private fun androidx.compose.foundation.lazy.LazyListScope.ocrSliders(
    redBorder: androidx.compose.runtime.MutableFloatState,
    whiteMin: androidx.compose.runtime.MutableFloatState,
    whiteMax: androidx.compose.runtime.MutableFloatState,
    brightFraction: androidx.compose.runtime.MutableFloatState,
    padTop: androidx.compose.runtime.MutableFloatState,
    padBottom: androidx.compose.runtime.MutableFloatState,
    sideRed: androidx.compose.runtime.MutableFloatState,
    vm: ScanDebugViewModel,
) {
    item { SliderRow("Rot-Gate Rand", redBorder.floatValue, 0.30f..0.95f, { redBorder.floatValue = it; ScanImageOps.titleBorderRed = it }, vm) }
    item { SliderRow("Weiß-Min Rand", whiteMin.floatValue, 0.0f..0.20f, { whiteMin.floatValue = it; ScanImageOps.titleBorderWhite = it }, vm) }
    item { SliderRow("Weiß-Max Text", whiteMax.floatValue, 0.02f..0.40f, { whiteMax.floatValue = it; ScanImageOps.titleTextWhite = it }, vm) }
    item { SliderRow("Weiß-Helligkeit", brightFraction.floatValue, 0.30f..0.90f, { brightFraction.floatValue = it; ScanImageOps.whiteTextBrightFraction = it }, vm) }
    item { SliderRow("Rand oben", padTop.floatValue, -0.5f..1.5f, { padTop.floatValue = it; ScanImageOps.titlePadTopFraction = it }, vm) }
    item { SliderRow("Rand unten", padBottom.floatValue, -0.5f..1.5f, { padBottom.floatValue = it; ScanImageOps.titlePadBottomFraction = it }, vm) }
    item { SliderRow("Seiten-Cut (Rot > x)", sideRed.floatValue, 0.50f..1.0f, { sideRed.floatValue = it; ScanImageOps.titleSideRed = it }, vm) }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    vm: ScanDebugViewModel,
    format: String = "%.2f",
) {
    Column {
        Text("$label: ${format.format(value)}", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = { vm.reanalyze() },
            valueRange = range,
        )
    }
}

@Composable
private fun SaveTemplateDialog(
    defaultQuery: String,
    search: (String) -> List<CardDefinition>,
    onPick: (CardDefinition) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(defaultQuery) }
    val results = remember(query) { search(query).take(30) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        title = { Text("Karte zuordnen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Karte suchen") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(
                    modifier = Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    results.forEach { card ->
                        TextButton(
                            onClick = { onPick(card) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(card.nameDe, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            }
        },
    )
}

@Composable
private fun StageCard(index: Int, stage: ScanStage) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("${index + 1}. ${stage.label}", style = MaterialTheme.typography.labelLarge)
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
private fun RegionCard(
    index: Int,
    region: ScanRegion,
    canAssign: Boolean,
    assignedName: String?,
    onAssign: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Region ${index + 1} · Band: ${region.band} · Konfidenz: ${region.confidence}",
                style = MaterialTheme.typography.labelLarge,
            )
            Image(
                bitmap = region.crop.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color.White),
                contentScale = ContentScale.Fit,
            )
            if (region.ocrText.isNotEmpty()) {
                Text("OCR: \"${region.ocrText}\"", style = MaterialTheme.typography.bodyMedium)
            }
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
            if (canAssign) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        "Zuordnung: ${assignedName ?: "—"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (assignedName != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = onAssign) {
                        Text(if (assignedName == null) "Karte wählen" else "Ändern")
                    }
                }
            }
        }
    }
}
