package com.gathin.transfer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A QR that has been tilted, blurred, and washed out, the way a TV screen looks to a phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QrPhotoTest {
    @Test
    fun decodesAGlaredQr() {
        val payload = "http://192.168.1.20:8080/"
        val code = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 33, 33)
        val plain = Bitmap.createBitmap(code.width * 8, code.height * 8, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(plain)
        val ink = Paint()
        for (y in 0 until code.height) for (x in 0 until code.width) {
            ink.color = if (code.get(x, y)) Color.BLACK else Color.WHITE
            canvas.drawRect((x * 8).toFloat(), (y * 8).toFloat(), ((x + 1) * 8).toFloat(), ((y + 1) * 8).toFloat(), ink)
        }
        assertEquals("plain", payload, QrDecoder.decode(plain))
        val photo = glare(payload)
        val text = QrDecoder.decode(photo)
        assertEquals("glare", payload, text)
        assertEquals("192.168.1.20", QrLink.parse(text)!!.host)
    }

    private fun glare(payload: String): Bitmap {
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 240, 240)
        val quiet = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(quiet)
        canvas.drawColor(Color.WHITE)
        val ink = Paint()
        val cell = 240f / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                ink.color = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                canvas.drawRect(40 + x * cell, 40 + y * cell, 40 + (x + 1) * cell, 40 + (y + 1) * cell, ink)
            }
        }
        val tilted = Bitmap.createBitmap(360, 360, Bitmap.Config.ARGB_8888)
        val out = Canvas(tilted)
        out.drawColor(Color.rgb(16, 20, 25))
        val map = Matrix()
        map.setPolyToPoly(
            floatArrayOf(0f, 0f, 320f, 0f, 0f, 320f, 320f, 320f), 0,
            floatArrayOf(24f, 18f, 332f, 28f, 16f, 336f, 328f, 318f), 0, 4
        )
        out.drawBitmap(quiet, map, Paint(Paint.FILTER_BITMAP_FLAG))
        val small = Bitmap.createScaledBitmap(tilted, 240, 240, true)
        val blur = Bitmap.createScaledBitmap(small, 360, 360, true)
        val wash = Canvas(blur)
        val glare = Paint()
        glare.color = Color.argb(40, 255, 255, 255)
        wash.drawCircle(70f, 60f, 50f, glare)
        return blur
    }
}
