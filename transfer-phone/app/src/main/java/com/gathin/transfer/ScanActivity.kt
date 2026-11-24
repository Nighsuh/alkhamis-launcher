package com.gathin.transfer

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** Reads the QR shown on the TV. The code is the page address plus the PIN. */
@Suppress("DEPRECATION")
class ScanActivity : AppCompatActivity(), SurfaceHolder.Callback {
    private var camera: Camera? = null
    private var done = false

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_scan)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 3)
        }
        findViewById<SurfaceView>(R.id.preview).holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        open(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        camera?.stopPreview()
        camera?.release()
        camera = null
    }

    private fun open(holder: SurfaceHolder) {
        try {
            val next = Camera.open(0)
            next.setPreviewDisplay(holder)
            next.setPreviewCallback { data, cam ->
                if (done || data == null) return@setPreviewCallback
                val size = cam.parameters.previewSize ?: return@setPreviewCallback
                try {
                    val source = PlanarYUVLuminanceSource(data, size.width, size.height, 0, 0, size.width, size.height, false)
                    val result = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)))
                    val target = QrLink.parse(result.text) ?: return@setPreviewCallback
                    done = true
                    runOnUiThread {
                        if (target.pin != null && target.pin.length == 6) {
                            try {
                                Books.factory(this).save(SavedTv(target.host + ":" + target.port, target.host, target.host, target.port, target.pin))
                            } catch (ignored: Exception) { }
                        }
                        startActivity(SessionActivity.intent(this, target.host, target.host, target.port, target.pin ?: ""))
                        finish()
                    }
                } catch (ignored: Exception) { }
            }
            next.startPreview()
            camera = next
        } catch (failed: Exception) {
            Toast.makeText(this, R.string.failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(request: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(request, permissions, results)
        if (request == 3 && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) {
            val holder = findViewById<SurfaceView>(R.id.preview).holder
            if (holder.surface != null) open(holder)
        }
    }
}
