package com.gathin.transfer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Size
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.slider.Slider
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX preview (the view sizes and rotates itself) plus ZXing on a 16:9 analysis stream.
 * 1.0 used the old Camera API on a SurfaceView with no display rotation and no focus, which
 * stretched and blurred the picture.
 */
class ScanActivity : AppCompatActivity() {
    private var camera: Camera? = null
    private var provider: ProcessCameraProvider? = null
    private val busy = AtomicBoolean(false)
    private var done = false
    private var torchOn = false
    private var lastTouch = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val analysis = Executors.newSingleThreadExecutor()
    private val hideZoom = Runnable { findViewById<TextView>(R.id.zoomReadout).visibility = android.view.View.GONE }
    private val refocus = object : Runnable {
        override fun run() {
            if (!done && System.currentTimeMillis() - lastTouch > 1200) focusCenter()
            handler.postDelayed(this, 4000)
        }
    }
    private val gallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) decodeStill(uri)
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_scan)
        val preview = findViewById<PreviewView>(R.id.preview)
        preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        preview.scaleType = PreviewView.ScaleType.FILL_CENTER
        findViewById<android.view.View>(R.id.close).setOnClickListener { finish() }
        findViewById<Button>(R.id.torch).setOnClickListener { toggleTorch() }
        findViewById<Button>(R.id.gallery).setOnClickListener { gallery.launch("image/*") }
        findViewById<Button>(R.id.allow).setOnClickListener { showRationale() }
        findViewById<Button>(R.id.zoom1).setOnClickListener { zoomTo(1f) }
        findViewById<Button>(R.id.zoom2).setOnClickListener { zoomTo(2f) }
        val slider = findViewById<Slider>(R.id.zoom)
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) camera?.cameraControl?.setZoomRatio(value)
            if (fromUser) showZoom(value)
        }
        wireTouches(preview)
        if (hasCamera()) startCamera() else showRationale()
    }

    override fun onResume() {
        super.onResume()
        if (hasCamera() && camera == null && !done) startCamera()
    }

    override fun onPause() {
        handler.removeCallbacks(refocus)
        handler.removeCallbacks(hideZoom)
        provider?.unbindAll()
        camera = null
        super.onPause()
    }

    override fun onDestroy() {
        analysis.shutdown()
        super.onDestroy()
    }

    private fun hasCamera(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun showRationale() {
        findViewById<Button>(R.id.allow).visibility = android.view.View.VISIBLE
        AlertDialog.Builder(this)
            .setMessage(R.string.scan_reason)
            .setPositiveButton(R.string.scan_allow) { _, _ -> requestPermissions(arrayOf(Manifest.permission.CAMERA), 3) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onRequestPermissionsResult(request: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(request, permissions, results)
        if (request != 3) return
        if (hasCamera()) {
            findViewById<Button>(R.id.allow).visibility = android.view.View.GONE
            startCamera()
        } else if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            AlertDialog.Builder(this)
                .setMessage(R.string.scan_denied)
                .setPositiveButton(R.string.scan_settings) { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isDestroyed || !hasCamera()) return@addListener
            val next = future.get()
            provider = next
            val selector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build()
            val rotation = findViewById<PreviewView>(R.id.preview).display?.rotation ?: android.view.Surface.ROTATION_0
            val preview = Preview.Builder().setResolutionSelector(selector).setTargetRotation(rotation).build()
            val analyzer = ImageAnalysis.Builder()
                .setResolutionSelector(selector)
                .setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
            analyzer.setAnalyzer(analysis) { image -> readFrame(image) }
            preview.setSurfaceProvider(findViewById<PreviewView>(R.id.preview).surfaceProvider)
            try {
                next.unbindAll()
                val bound = next.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
                camera = bound
                val state = bound.cameraInfo.zoomState.value
                val slider = findViewById<Slider>(R.id.zoom)
                slider.valueFrom = state?.minZoomRatio ?: 1f
                slider.valueTo = (state?.maxZoomRatio ?: 4f).coerceAtLeast(slider.valueFrom + 0.1f)
                slider.value = state?.zoomRatio ?: 1f
                bound.cameraInfo.zoomState.observe(this) { zoom ->
                    if (!slider.isPressed) slider.value = zoom.zoomRatio.coerceIn(slider.valueFrom, slider.valueTo)
                }
                focusCenter()
                handler.removeCallbacks(refocus)
                handler.post(refocus)
            } catch (failed: Exception) {
                Toast.makeText(this, R.string.failed, Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun readFrame(image: ImageProxy) {
        try {
            if (done || !busy.compareAndSet(false, true)) return
            val bitmap = image.toBitmap()
            val text = QrDecoder.decode(bitmap)
            if (text != null) runOnUiThread { onRead(text) }
        } catch (ignored: Exception) {
        } finally {
            busy.set(false)
            image.close()
        }
    }

    private fun yPlane(image: ImageProxy): ByteArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        buffer.rewind()
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val out = ByteArray(width * height)
        val row = ByteArray(rowStride)
        var pos = 0
        for (y in 0 until height) {
            val take = minOf(rowStride, buffer.remaining())
            if (take <= 0) break
            buffer.get(row, 0, take)
            System.arraycopy(row, 0, out, pos, minOf(width, take))
            pos += width
        }
        return out
    }

    private fun onRead(text: String) {
        if (done) return
        val target = QrLink.parse(text)
        if (target == null) {
            AlertDialog.Builder(this)
                .setTitle(R.string.scan_unknown)
                .setMessage(getString(R.string.scan_read, text))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            done = true
            handler.postDelayed({ done = false }, 1500)
            return
        }
        done = true
        vibrate()
        try {
            Books.factory(this).save(SavedTv(target.host + ":" + target.port, target.host, target.host, target.port, ""))
        } catch (ignored: Exception) { }
        startActivity(SessionActivity.intent(this, target.host, target.host, target.port, ""))
        finish()
    }

    private fun decodeStill(uri: Uri) {
        val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return
        val text = QrDecoder.decode(bitmap)
        if (text == null) Toast.makeText(this, R.string.scan_unknown, Toast.LENGTH_SHORT).show()
        else onRead(text)
    }

    private fun focusCenter() {
        val preview = findViewById<PreviewView>(R.id.preview)
        val factory = preview.meteringPointFactory
        val point = factory.createPoint(preview.width / 2f, preview.height / 2f)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    private fun focusAt(x: Float, y: Float) {
        lastTouch = System.currentTimeMillis()
        val point = findViewById<PreviewView>(R.id.preview).meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    private fun zoomTo(ratio: Float) {
        val state = camera?.cameraInfo?.zoomState?.value ?: return
        val next = ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio)
        camera?.cameraControl?.setZoomRatio(next)
        showZoom(next)
    }

    private fun showZoom(ratio: Float) {
        val label = findViewById<TextView>(R.id.zoomReadout)
        label.text = String.format(java.util.Locale.US, "%.1fx", ratio)
        label.visibility = android.view.View.VISIBLE
        handler.removeCallbacks(hideZoom)
        handler.postDelayed(hideZoom, 1200)
    }

    private fun toggleTorch() {
        torchOn = !torchOn
        camera?.cameraControl?.enableTorch(torchOn)
    }

    private fun vibrate() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun wireTouches(preview: PreviewView) {
        val scale = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val state = camera?.cameraInfo?.zoomState?.value ?: return true
                val next = (state.zoomRatio * detector.scaleFactor).coerceIn(state.minZoomRatio, state.maxZoomRatio)
                camera?.cameraControl?.setZoomRatio(next)
                showZoom(next)
                return true
            }
        })
        val taps = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                focusAt(event.x, event.y)
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                val current = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                zoomTo(if (current < 1.5f) 2f else 1f)
                return true
            }
        })
        preview.setOnTouchListener { _, event ->
            scale.onTouchEvent(event)
            if (!scale.isInProgress) taps.onTouchEvent(event)
            true
        }
    }
}
