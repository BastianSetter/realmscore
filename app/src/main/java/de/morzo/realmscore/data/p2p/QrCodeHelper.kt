package de.morzo.realmscore.data.p2p

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter

/**
 * QR generation and decoding via ZXing-core (Phase 28). Pure ZXing — no `zxing-android-embedded`,
 * no ML Kit — so it works identically in both the fdroid and play flavors and stays F-Droid-safe.
 */
object QrCodeHelper {

    /** Renders [content] into a square black-on-white QR [Bitmap] of [sizePx] pixels per side. */
    fun generate(content: String, sizePx: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    /**
     * Decodes a QR code from a camera frame's luminance plane (CameraX `ImageProxy` Y-plane). Returns
     * the decoded text or `null` if no QR was found in this frame. [rowStride] lets us pass the raw
     * Y-plane buffer directly (it may be wider than [width] for alignment).
     */
    fun decodeLuminance(
        luminance: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int = width,
    ): String? {
        // The Y-plane buffer may have row padding (rowStride > width) and its last row can be short,
        // so clamp the crop height to what the array actually holds — otherwise PlanarYUVLuminanceSource
        // throws IllegalArgumentException and the whole frame is dropped.
        val stride = rowStride.coerceAtLeast(width)
        val usableHeight = (luminance.size / stride).coerceIn(1, height)
        val source = PlanarYUVLuminanceSource(
            luminance,
            stride,
            usableHeight,
            0,
            0,
            width,
            usableHeight,
            false,
        )
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                ),
            )
        }
        // Try the normal bitmap, then its inverted form (some screens render light-on-dark QR).
        for (binary in sequenceOf(
            BinaryBitmap(HybridBinarizer(source)),
            BinaryBitmap(HybridBinarizer(source.invert())),
        )) {
            try {
                return reader.decodeWithState(binary).text
            } catch (_: NotFoundException) {
                reader.reset()
            } catch (_: Exception) {
                reader.reset()
            }
        }
        return null
    }
}
