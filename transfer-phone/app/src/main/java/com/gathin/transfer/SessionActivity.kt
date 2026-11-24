package com.gathin.transfer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class SessionActivity : AppCompatActivity() {
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_session)
        val name = intent.getStringExtra("name") ?: ""
        val host = intent.getStringExtra("host") ?: ""
        val port = intent.getIntExtra("port", 8080)
        val pin = intent.getStringExtra("pin") ?: ""
        findViewById<TextView>(R.id.tvName).text = name
        val bar = findViewById<ProgressBar>(R.id.progress)
        val label = findViewById<TextView>(R.id.progressLabel)
        val previewPct = intent.getIntExtra("previewProgress", -1)
        if (previewPct >= 0) {
            bar.progress = previewPct
            label.text = getString(R.string.sending, intent.getStringExtra("previewFile") ?: "")
            findViewById<TextView>(R.id.progressPercent).text = "$previewPct%"
        }
        findViewById<Button>(R.id.pick).setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), 4)
        }
        val preview = intent.getStringArrayExtra("previewFiles")
        if (preview != null) {
            val sizes = mapOf("clip.mp4" to 248_000_000L, "photo.jpg" to 3_400_000L, "app.apk" to 21_000_000L)
            show(preview.map { RemoteFile(it, sizes[it] ?: 1024L, System.currentTimeMillis()) })
        }
        else load(host, port, pin)
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request != 4 || result != RESULT_OK || data == null) return
        val host = intent.getStringExtra("host") ?: return
        val port = intent.getIntExtra("port", 8080)
        val pin = intent.getStringExtra("pin") ?: return
        val uris = ArrayList<Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
        } else if (data.data != null) uris.add(data.data!!)
        for (uri in uris) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val label = uri.lastPathSegment ?: "file"
            UploadService.enqueue(this, Job(java.util.UUID.randomUUID().toString(), host, port, pin, label, uri.toString(), null, 0))
        }
    }

    private fun load(host: String, port: Int, pin: String) {
        Thread {
            try {
                val files = TvClient(host, port, pin).list()
                runOnUiThread { show(files) }
            } catch (rejected: PinRejected) {
                try { Books.factory(this).forgetPin(host, port) } catch (ignored: Exception) { }
                runOnUiThread { Toast.makeText(this, R.string.pin_wrong, Toast.LENGTH_LONG).show() }
            } catch (failed: Exception) {
                runOnUiThread { Toast.makeText(this, R.string.failed, Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun show(files: List<RemoteFile>) {
        val box = findViewById<LinearLayout>(R.id.files)
        box.removeAllViews()
        val host = intent.getStringExtra("host") ?: ""
        val port = intent.getIntExtra("port", 8080)
        val pin = intent.getStringExtra("pin") ?: ""
        for (file in files) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.setBackgroundResource(R.drawable.card)
            val pad = dp(16)
            row.setPadding(pad, pad, pad, pad)
            val head = LinearLayout(this)
            head.orientation = LinearLayout.HORIZONTAL
            head.gravity = android.view.Gravity.CENTER_VERTICAL
            val icon = android.widget.ImageView(this)
            icon.setImageResource(kindIcon(file.name))
            val boxSize = dp(44)
            icon.layoutParams = LinearLayout.LayoutParams(boxSize, boxSize)
            val text = LinearLayout(this)
            text.orientation = LinearLayout.VERTICAL
            val textLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            textLp.marginStart = dp(14)
            text.layoutParams = textLp
            val name = TextView(this)
            name.text = file.name
            name.setTextColor(0xFFFFFFFF.toInt())
            name.textSize = 18f
            val size = TextView(this)
            size.text = readableSize(file.size)
            size.setTextColor(0xFFACB8C1.toInt())
            size.textSize = 16f
            text.addView(name)
            text.addView(size)
            head.addView(icon)
            head.addView(text)
            row.addView(head)
            val actions = LinearLayout(this)
            actions.orientation = LinearLayout.HORIZONTAL
            val actionsLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            actionsLp.topMargin = dp(12)
            actions.layoutParams = actionsLp
            actions.addView(chip(R.string.download) { download(host, port, pin, file.name) })
            actions.addView(chip(R.string.delete) { Thread { TvClient(host, port, pin).delete(file.name); runOnUiThread { load(host, port, pin) } }.start() })
            row.addView(actions)
            if (file.name.endsWith(".apk", true)) {
                val install = chip(R.string.install_tv) { Thread { TvClient(host, port, pin).install(file.name) }.start() }
                val wide = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                wide.topMargin = dp(8)
                install.layoutParams = wide
                install.maxLines = 1
                row.addView(install)
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12)
            row.layoutParams = lp
            box.addView(row)
        }
    }

    private fun kindIcon(name: String): Int {
        val n = name.lowercase()
        return when {
            n.endsWith(".apk") -> R.drawable.ic_kind_apk
            n.endsWith(".mp4") || n.endsWith(".mkv") -> R.drawable.ic_kind_video
            n.endsWith(".jpg") || n.endsWith(".png") || n.endsWith(".webp") -> R.drawable.ic_kind_image
            else -> R.drawable.ic_kind_file
        }
    }

    private fun readableSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    }

    private fun chip(label: Int, click: () -> Unit): Button {
        val button = Button(this)
        button.setText(label)
        button.setAllCaps(false)
        button.setTextColor(0xFFFFFFFF.toInt())
        button.textSize = 16f
        button.minHeight = dp(48)
        button.setBackgroundColor(0xFF2C3A34.toInt())
        button.setOnClickListener { click() }
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.marginEnd = dp(8)
        button.layoutParams = lp
        return button
    }

    private fun download(host: String, port: Int, pin: String, name: String) {
        Thread {
            try {
                val client = TvClient(host, port, pin)
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = android.content.ContentValues()
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AlkhamisTransfer")
                    values.put(MediaStore.MediaColumns.IS_PENDING, 1)
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@Thread
                    contentResolver.openOutputStream(uri)?.use { out -> client.download(name, out) }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                } else {
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AlkhamisTransfer")
                    if (!dir.isDirectory) dir.mkdirs()
                    FileOutputStream(File(dir, name)).use { client.download(name, it) }
                }
                runOnUiThread { Toast.makeText(this, R.string.download, Toast.LENGTH_SHORT).show() }
            } catch (failed: Exception) {
                runOnUiThread { Toast.makeText(this, R.string.failed, Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    companion object {
        fun intent(ctx: Activity, name: String, host: String, port: Int, pin: String): Intent {
            return Intent(ctx, SessionActivity::class.java)
                .putExtra("name", name)
                .putExtra("host", host)
                .putExtra("port", port)
                .putExtra("pin", pin)
        }
    }
}
