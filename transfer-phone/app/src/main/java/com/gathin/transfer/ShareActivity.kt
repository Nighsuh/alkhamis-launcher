package com.gathin.transfer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** What the system share sheet opens. The label in that sheet is the app name. */
class ShareActivity : AppCompatActivity() {
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_share)
        val names = ArrayList<String>()
        val uris = ArrayList<Uri>()
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (Intent.ACTION_SEND_MULTIPLE == intent.action) {
            val list = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            if (list != null) uris.addAll(list)
        } else {
            val one = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (one != null) uris.add(one)
        }
        val box = findViewById<LinearLayout>(R.id.shared)
        for (uri in uris) {
            val label = uri.lastPathSegment ?: uri.toString()
            names.add(label)
            box.addView(line(label))
        }
        if (!text.isNullOrBlank()) {
            names.add(text)
            box.addView(line(text))
        }
        val targets = findViewById<LinearLayout>(R.id.targets)
        val saved = try { Books.factory(this).list() } catch (e: Exception) { emptyList() }
        val shown = if (intent.hasExtra("previewTv")) {
            listOf(SavedTv("preview", intent.getStringExtra("previewTv") ?: "", "10.0.0.8", 8080, "123456"))
        } else saved
        for (tv in shown) {
            val button = Button(this)
            button.text = tv.name
            button.setTextColor(0xFF101419.toInt())
            button.setBackgroundResource(R.drawable.gold_button)
            button.setOnClickListener { send(tv, uris, text) }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(10)
            button.layoutParams = lp
            targets.addView(button)
        }
    }

    private fun line(text: String): TextView {
        val view = TextView(this)
        view.text = text
        view.setTextColor(0xFFFFFFFF.toInt())
        view.textSize = 16f
        view.setPadding(0, dp(6), 0, dp(6))
        return view
    }

    private fun send(tv: SavedTv, uris: List<Uri>, text: String?) {
        for (uri in uris) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (ignored: Exception) { }
            UploadService.enqueue(this, Job(java.util.UUID.randomUUID().toString(), tv.host, tv.port, tv.pin,
                uri.lastPathSegment ?: "file", uri.toString(), null, 0))
        }
        if (!text.isNullOrBlank()) {
            UploadService.enqueue(this, Job(java.util.UUID.randomUUID().toString(), tv.host, tv.port, tv.pin,
                text.take(40), null, text, 0))
        }
        finish()
    }
}
