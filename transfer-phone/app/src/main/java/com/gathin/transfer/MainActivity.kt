package com.gathin.transfer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class MainActivity : AppCompatActivity() {
    private val found = ArrayList<FoundTv>()

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_tvs)
        findViewById<Button>(R.id.language).setOnClickListener { chooseLanguage() }
        findViewById<Button>(R.id.manual).setOnClickListener { askAddress() }
        paintLanguage()
        findViewById<Button>(R.id.scan).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }
        Discovery.preview?.let { found.addAll(it) }
        paint()
        if (Discovery.preview == null) startSearch()
    }

    private fun startSearch() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.NEARBY_WIFI_DEVICES) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.NEARBY_WIFI_DEVICES), 41)
            return
        }
        if (android.os.Build.VERSION.SDK_INT < 33 &&
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 41)
            return
        }
        Discovery.start(this) { tv ->
            runOnUiThread {
                if (found.none { it.host == tv.host && it.port == tv.port }) found.add(tv)
                paint()
            }
        }
    }

    override fun onRequestPermissionsResult(request: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(request, permissions, results)
        if (request == 41) {
            if (results.isNotEmpty() && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) startSearch()
            else Toast.makeText(this, R.string.err_permission, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        if (isFinishing) Discovery.stop()
        super.onDestroy()
    }

    private fun paintLanguage() {
        val current = AppCompatDelegate.getApplicationLocales()
        val label = when {
            current.isEmpty -> getString(R.string.language_system)
            current[0]?.language == "ar" -> getString(R.string.language_arabic)
            else -> getString(R.string.language_english)
        }
        findViewById<Button>(R.id.language).text = getString(R.string.language) + "  ·  " + label
    }

    private fun chooseLanguage() {
        val tags = arrayOf("", "ar", "en")
        val labels = arrayOf(
            getString(R.string.language_system),
            getString(R.string.language_arabic),
            getString(R.string.language_english)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.language)
            .setItems(labels) { _, which ->
                val locales = if (tags[which].isEmpty()) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(tags[which])
                AppCompatDelegate.setApplicationLocales(locales)
            }
            .show()
    }

    private fun paint() {
        val box = findViewById<LinearLayout>(R.id.tvs)
        val empty = findViewById<View>(R.id.empty)
        box.removeAllViews()
        val saved = try {
            Books.factory(this).list()
        } catch (e: Exception) {
            emptyList()
        }
        empty.visibility = if (found.isEmpty() && saved.isEmpty()) View.VISIBLE else View.GONE
        for (tv in saved) box.addView(row(tv.name, getString(R.string.saved), tv.host + ":" + tv.port, tv))
        for (tv in found) box.addView(row(tv.name, getString(R.string.found), tv.host + ":" + tv.port,
            SavedTv(tv.host + ":" + tv.port, tv.name, tv.host, tv.port, "")))
    }

    private fun row(title: String, status: String, sub: String, saved: SavedTv?): View {
        val line = LinearLayout(this)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL
        line.minimumHeight = dp(88)
        line.setBackgroundResource(R.drawable.card)
        val pad = dp(18)
        line.setPadding(pad, pad, pad, pad)
        val icon = ImageView(this)
        icon.setImageResource(R.drawable.ic_tv)
        val box = dp(48)
        icon.layoutParams = LinearLayout.LayoutParams(box, box)
        val text = LinearLayout(this)
        text.orientation = LinearLayout.VERTICAL
        val textLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        textLp.marginStart = dp(16)
        text.layoutParams = textLp
        val name = TextView(this)
        name.text = title
        name.textSize = 22f
        name.setTextColor(0xFFFFFFFF.toInt())
        val chip = TextView(this)
        chip.text = status
        chip.textSize = 14f
        chip.setTextColor(0xFFE6C57A.toInt())
        chip.setBackgroundResource(R.drawable.chip_bg)
        chip.setPadding(dp(10), dp(4), dp(10), dp(4))
        val chipLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        chipLp.topMargin = dp(6)
        chip.layoutParams = chipLp
        val meta = TextView(this)
        meta.text = sub
        meta.textSize = 16f
        meta.setTextColor(0xFFACB8C1.toInt())
        val metaLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        metaLp.topMargin = dp(6)
        meta.layoutParams = metaLp
        text.addView(name)
        text.addView(chip)
        text.addView(meta)
        line.addView(icon)
        line.addView(text)
        val host = sub.substringBefore(':')
        val port = sub.substringAfter(':', "8080").toIntOrNull() ?: 8080
        line.setOnClickListener { open(title, host, port) }
        if (saved != null) {
            val rename = android.widget.Button(this)
            rename.setText(R.string.rename)
            rename.setAllCaps(false)
            rename.textSize = 16f
            rename.minHeight = dp(48)
            rename.setTextColor(0xFFE6C57A.toInt())
            rename.setBackgroundColor(0x00000000)
            rename.setOnClickListener { rename(saved) }
            text.addView(rename)
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = (10 * resources.displayMetrics.density).toInt()
        line.layoutParams = lp
        line.gravity = Gravity.START
        return line
    }

    private fun askAddress() {
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        val host = EditText(this)
        host.hint = getString(R.string.host_hint)
        val port = EditText(this)
        port.hint = getString(R.string.port_hint)
        port.setText("8080")
        wrap.addView(host)
        wrap.addView(port)
        AlertDialog.Builder(this)
            .setTitle(R.string.manual)
            .setView(wrap)
            .setPositiveButton(R.string.enter) { _, _ ->
                val address = host.text.toString().trim()
                val number = port.text.toString().toIntOrNull() ?: 8080
                if (address.isNotEmpty()) open(address, address, number)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun open(name: String, host: String, port: Int) {
        try {
            Books.factory(this).save(SavedTv("$host:$port", name, host, port, ""))
        } catch (ignored: Exception) { }
        startActivity(SessionActivity.intent(this, name, host, port, ""))
    }

    private fun rename(tv: SavedTv) {
        val field = EditText(this)
        field.setText(tv.name)
        field.setSingleLine(true)
        AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(field)
            .setPositiveButton(R.string.enter) { _, _ ->
                val next = field.text.toString().trim()
                if (next.isEmpty()) return@setPositiveButton
                Thread {
                    try {
                        TvClient(tv.host, tv.port).rename(next)
                        Books.factory(this).save(tv.copy(name = next))
                        runOnUiThread {
                            Toast.makeText(this, R.string.renamed, Toast.LENGTH_SHORT).show()
                            paint()
                        }
                    } catch (failed: Exception) {
                        runOnUiThread { Toast.makeText(this, explain(failed), Toast.LENGTH_LONG).show() }
                    }
                }.start()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

fun explain(failed: Exception): Int {
    val kind = (failed as? TvFailure)?.kind
    return when (kind) {
        TvFailure.Kind.TIMEOUT -> R.string.err_timeout
        TvFailure.Kind.REFUSED -> R.string.err_refused
        TvFailure.Kind.UNREACHABLE -> R.string.err_unreachable
        TvFailure.Kind.HTTP -> R.string.err_http
        else -> R.string.failed
    }
}

fun Activity.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
