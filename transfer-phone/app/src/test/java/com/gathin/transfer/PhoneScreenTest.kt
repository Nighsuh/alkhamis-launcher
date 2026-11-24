package com.gathin.transfer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "ar-w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhoneScreenTest {
    @Test
    fun qrCarriesThePin() {
        val target = QrLink.parse("http://10.0.0.8:8080/?p=123456")
        assertEquals("10.0.0.8", target!!.host)
        assertEquals(8080, target.port)
        assertEquals("123456", target.pin)
        val ctx = RuntimeEnvironment.getApplication()
        JobStore.save(ctx, emptyList())
        JobStore.add(ctx, Job("a", "10.0.0.8", 8080, "123456", "note", null, "hello", 0))
        assertEquals("hello", JobStore.load(ctx)[0].text)
        JobStore.remove(ctx, "a")
        assertTrue(JobStore.load(ctx).isEmpty())
    }

    @Test
    fun drawsThePhoneScreens() {
        Books.factory = {
            MemoryBook().apply { save(SavedTv("1", "الصالة", "192.168.1.20", 8080, "123456")) }
        }
        Discovery.preview = listOf(FoundTv("Alkhamis-1a2b", "192.168.1.21", 8080))
        val home = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals("Alkhamis TV Transfer", home.findViewById<TextView>(R.id.title).text.toString())
        draw(home.window.decorView, "tvs.png")
        home.finish()

        val session = Robolectric.buildActivity(SessionActivity::class.java, Intent()
            .putExtra("name", "الصالة")
            .putExtra("host", "192.168.1.20")
            .putExtra("port", 8080)
            .putExtra("pin", "123456")
            .putExtra("previewProgress", 62)
            .putExtra("previewFile", "clip.mp4")
            .putExtra("previewFiles", arrayOf("clip.mp4", "photo.jpg", "app.apk"))).setup().get()
        assertTrue(session.findViewById<TextView>(R.id.progressLabel).text.toString().contains("clip.mp4"))
        draw(session.window.decorView, "send.png")
        draw(session.window.decorView, "files.png")
        session.finish()

        val share = Robolectric.buildActivity(ShareActivity::class.java, Intent(Intent.ACTION_SEND)
            .setType("*/*")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://media/external/video/clip.mp4"))
            .putExtra(Intent.EXTRA_TEXT, "https://example.com/a")
            .putExtra("previewTv", "الصالة")).setup().get()
        draw(share.window.decorView, "share.png")
        val stored = JobStore.load(RuntimeEnvironment.getApplication())
        assertTrue(stored.isEmpty() || stored[0].attempts == 0)
        share.finish()
        org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.CAMERA)
        val scan = Robolectric.buildActivity(ScanActivity::class.java).setup().get()
        assertEquals("Alkhamis TV Transfer", scan.findViewById<TextView>(R.id.title).text.toString())
        assertTrue(scan.findViewById<TextView>(R.id.hint).text.toString().contains("الكاميرا"))
        draw(scan.window.decorView, "scan.png")
        scan.finish()
        Discovery.preview = null
    }

    @Test
    fun decodesTheLauncherQr() {
        val payload = "http://172.30.0.2:8080/?p=775441"
        val target = QrLink.parse(payload)
        assertEquals("172.30.0.2", target!!.host)
        assertEquals(8080, target.port)
        assertEquals("775441", target.pin)
        assertEquals(null, QrLink.parse("not-a-tv"))
        assertEquals(null, QrLink.parse("http://10.0.0.8:8080/?p=12"))
        assertEquals(payload, QrDecoder.decode(paintQr(payload, false)))
        assertEquals(payload, QrDecoder.decode(paintQr(payload, true)))
    }

    private fun paintQr(payload: String, inverted: Boolean): Bitmap {
        val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(payload, com.google.zxing.BarcodeFormat.QR_CODE, 280, 280)
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.parseColor("#101419"))
        val paint = android.graphics.Paint()
        val ox = 60f
        val oy = 60f
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                val dark = matrix.get(x, y)
                val ink = if (inverted) !dark else dark
                paint.color = if (ink) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                canvas.drawRect(ox + x, oy + y, ox + x + 1, oy + y + 1, paint)
            }
        }
        return bitmap
    }

    private fun draw(view: View, name: String) {
        val width = 1080
        val height = 2400
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("/opt/cursor/artifacts/qa/transfer-app-1.0.2")
        dir.mkdirs()
        FileOutputStream(File(dir, name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
