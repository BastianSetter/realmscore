package de.morzo.realmscore.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.R
import de.morzo.realmscore.data.ocr.CardScanner
import de.morzo.realmscore.data.ocr.RedBannerDetector
import de.morzo.realmscore.domain.model.CardDefinition

/**
 * Camera card scan (Phase 26), rendered *inside* RoundCaptureScreen's scaffold (so the player
 * dropdown stays usable — the user can switch which hand / the Mittelfeld to scan). A single photo is
 * recognised into best-match cards ([onScanComplete]); [onManual] drops to manual entry (also used
 * when the camera permission is denied). No confirmation sheet — correction happens in the player
 * stage.
 */
@Composable
fun CameraScanScreen(
    scanner: CardScanner,
    maxCards: Int,
    entryKey: String,
    excludedKeys: Set<String>,
    onScanComplete: (cards: List<CardDefinition>, skippedConflicts: Int) -> Unit,
    onManual: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Key by the entry so each hand/Mittelfeld gets a fresh VM with the right slot count and no
    // carried-over recognition result.
    val vm: CameraScanViewModel = viewModel(
        key = "camera-scan-$entryKey",
        factory = CameraScanViewModel.Factory(scanner, maxCards, excludedKeys),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) onManual()
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Recognition done → hand the cards back; the parent fills slots and advances to the player stage.
    LaunchedEffect(state.result) {
        state.result?.let { onScanComplete(it, state.skippedConflicts) }
    }

    if (!hasPermission) {
        PermissionRationale(
            onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onManual = onManual,
            modifier = modifier,
        )
        return
    }

    // 1 stack for a 7-card hand, 2 side-by-side stacks for the 10/12 Mittelfeld (mirrors the detector).
    val columns = remember(maxCards) { RedBannerDetector.columnsFor(maxCards) }

    val imageCapture = remember { ImageCapture.Builder().build() }
    var flashOn by remember { mutableStateOf(false) }
    LaunchedEffect(flashOn) {
        imageCapture.flashMode =
            if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    val capture: () -> Unit = capture@{
        if (state.isRecognizing) return@capture
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotation = image.imageInfo.rotationDegrees
                    val bitmap = image.toBitmap()
                    image.close()
                    vm.recognize(bitmap, rotation)
                }

                override fun onError(exception: ImageCaptureException) {
                    vm.dismissFailure()
                }
            },
        )
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        // Tesseract needs the cards arranged as a fan; ML Kit takes a free whole-hand photo. Show the
        // dashed guide (1 stack for a hand, 2 side-by-side stacks for the 10/12 Mittelfeld) only for the
        // fan engine, so the user knows how to lay the cards. The overlay is purely visual (no touch).
        if (scanner.usesFanLayout) {
            FanGuideOverlay(
                columns = columns,
                cardCount = maxCards,
                modifier = Modifier.fillMaxSize(),
            )
        }

        IconButton(
            onClick = { flashOn = !flashOn },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
            Icon(
                imageVector = if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = stringResource(R.string.camera_scan_flash),
                tint = Color.White,
            )
        }

        // Hint at the top of the preview (clear of the flash button on the right), so the bottom
        // controls stay uncluttered and the guide frame below it is unobstructed.
        Text(
            text = when {
                state.recognitionFailed -> stringResource(R.string.camera_scan_failed)
                scanner.usesFanLayout && columns >= 2 ->
                    stringResource(R.string.camera_scan_hint_fan_double)
                scanner.usesFanLayout -> stringResource(R.string.camera_scan_hint_fan_single)
                else -> stringResource(R.string.camera_scan_hint)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 16.dp, start = 56.dp, end = 56.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FloatingActionButton(onClick = capture) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = stringResource(R.string.camera_scan_capture),
                )
            }
            OutlinedButton(onClick = onManual) {
                Text(stringResource(R.string.camera_scan_manual))
            }
        }

        if (state.isRecognizing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.camera_scan_recognizing),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

/**
 * Dashed guide showing where to lay the fanned cards: one column frame per stack ([columns] = 1 for a
 * 7-card hand, 2 for the 10/12 Mittelfeld), with a horizontal dashed line per card to suggest the
 * top→bottom fan. Purely cosmetic — it draws on the camera preview and never intercepts touches.
 */
@Composable
private fun FanGuideOverlay(
    columns: Int,
    cardCount: Int,
    modifier: Modifier = Modifier,
) {
    val guideColor = Color.White.copy(alpha = 0.85f)
    // Spread the cards across the columns as evenly as possible (10 → 5/5, 12 → 6/6, 7 → 7).
    val perColumn = IntArray(columns) { i ->
        cardCount / columns + if (i < cardCount % columns) 1 else 0
    }
    Canvas(modifier) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(22f, 16f), 0f)
        val frameStroke = Stroke(width = 3.dp.toPx(), pathEffect = dash)
        val lineWidth = 2.dp.toPx()

        val marginX = size.width * 0.08f
        val marginY = size.height * 0.16f
        val areaWidth = size.width - 2 * marginX
        val areaHeight = size.height - 2 * marginY
        val gap = if (columns >= 2) size.width * 0.04f else 0f
        val columnWidth = (areaWidth - gap * (columns - 1)) / columns
        val corner = CornerRadius(16.dp.toPx(), 16.dp.toPx())

        for (c in 0 until columns) {
            val left = marginX + c * (columnWidth + gap)
            drawRoundRect(
                color = guideColor,
                topLeft = Offset(left, marginY),
                size = Size(columnWidth, areaHeight),
                cornerRadius = corner,
                style = frameStroke,
            )
            // One divider per card boundary hints the overlapping fan (skip the outer frame edges).
            val cards = perColumn[c].coerceAtLeast(1)
            val step = areaHeight / cards
            for (i in 1 until cards) {
                val y = marginY + i * step
                drawLine(
                    color = guideColor,
                    start = Offset(left, y),
                    end = Offset(left + columnWidth, y),
                    strokeWidth = lineWidth,
                    pathEffect = dash,
                )
            }
        }
    }
}

@Composable
private fun PermissionRationale(
    onGrant: () -> Unit,
    onManual: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.camera_scan_permission_rationale),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.camera_scan_permission_open_settings))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.camera_scan_manual))
        }
    }
}
