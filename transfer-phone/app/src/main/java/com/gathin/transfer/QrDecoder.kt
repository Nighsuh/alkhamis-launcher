package com.gathin.transfer

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

/**
 * ML Kit first (the model is in the app, no Play Services), then ZXing.
 * A TV screen is tried whole, cropped to the scan frame, and smaller, with both
 * binarizers and an inverted copy, because glare and moiré defeat a single pass.
 */
object QrDecoder {
    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )
    @Volatile private var mlKit = true

    fun decode(bitmap: Bitmap): String? {
        val frames = ArrayList<Bitmap>(4)
        frames.add(bitmap)
        center(bitmap)?.let { frames.add(it) }
        smaller(bitmap, 640)?.let { frames.add(it) }
        for (frame in frames) {
            ml(frame)?.let { return it }
            zxing(frame)?.let { return it }
        }
        return null
    }

    fun decodeYuv(data: ByteArray, width: Int, height: Int, rotationDegrees: Int): String? {
        if (width <= 0 || height <= 0 || data.size < width * height) return null
        val pixels = IntArray(width * height)
        for (i in 0 until width * height) {
            val y = data[i].toInt() and 0xff
            pixels[i] = (0xff shl 24) or (y shl 16) or (y shl 8) or y
        }
        var bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val turns = ((rotationDegrees % 360) + 360) % 360 / 90
        repeat(turns) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(90f)
            val next = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (next != bitmap) bitmap.recycle()
            bitmap = next
        }
        return decode(bitmap)
    }

    private fun ml(bitmap: Bitmap): String? {
        if (!mlKit) return null
        return try {
            val scanner = BarcodeScanning.getClient(
                com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
            )
            val codes = Tasks.await(scanner.process(InputImage.fromBitmap(bitmap, 0)), 2, TimeUnit.SECONDS)
            scanner.close()
            codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
        } catch (failed: Throwable) {
            if (failed is UnsatisfiedLinkError || failed is NoClassDefFoundError || failed is ExceptionInInitializerError) {
                mlKit = false
            }
            null
        }
    }

    private fun zxing(bitmap: Bitmap): String? {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        read(source)?.let { return it }
        read(source.invert())?.let { return it }
        return null
    }

    private fun read(source: LuminanceSource): String? {
        val reader = MultiFormatReader()
        reader.setHints(hints)
        try {
            return reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (ignored: Exception) {
        } finally {
            reader.reset()
        }
        return try {
            reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(source))).text
        } catch (ignored: Exception) {
            null
        } finally {
            reader.reset()
        }
    }

    private fun center(bitmap: Bitmap): Bitmap? {
        val side = (minOf(bitmap.width, bitmap.height) * 0.72f).toInt()
        if (side < 40 || side >= bitmap.width || side >= bitmap.height) return null
        val left = (bitmap.width - side) / 2
        val top = (bitmap.height - side) / 2
        return Bitmap.createBitmap(bitmap, left, top, side, side)
    }

    private fun smaller(bitmap: Bitmap, maxSide: Int): Bitmap? {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSide) return null
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1), true)
    }
}
