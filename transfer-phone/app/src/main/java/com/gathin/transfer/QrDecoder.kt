package com.gathin.transfer

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** QR only, trying harder, then the same picture with the lights inverted. */
object QrDecoder {
    fun decode(bitmap: Bitmap): String? {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return decode(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))
    }

    fun decodeYuv(data: ByteArray, width: Int, height: Int, rotationDegrees: Int): String? {
        if (width <= 0 || height <= 0 || data.size < width * height) return null
        var source: LuminanceSource = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        val turns = ((rotationDegrees % 360) + 360) % 360 / 90
        repeat(turns) { source = source.rotateCounterClockwise() }
        return decode(source)
    }

    private fun decode(source: LuminanceSource): String? {
        read(source)?.let { return it }
        return read(source.invert())
    }

    private fun read(source: LuminanceSource): String? {
        val reader = MultiFormatReader()
        reader.setHints(mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        ))
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (ignored: Exception) {
            null
        } finally {
            reader.reset()
        }
    }
}
