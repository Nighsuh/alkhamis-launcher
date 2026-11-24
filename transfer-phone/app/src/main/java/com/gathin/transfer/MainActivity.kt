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

class MainActivity : AppCompatActivity() {
    private val found = ArrayList<FoundTv>()

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_tvs)
        findViewById<Button>(R.id.manual).setOnClickListener { askAddress() }
        findViewById<Button>(R.id.scan).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }
        Discovery.preview?.let { found.addAll(it) }
        paint()
        if (Discovery.preview == null) {
            Discovery.start(this) { tv ->
                runOnUiThread {
                    if (found.none { it.host == tv.host && it.port == tv.port }) found.add(tv)
                    paint()
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) Discovery.stop()
        super.onDestroy()
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
        for (tv in saved) box.addView(row(tv.name, getString(R.string.saved), tv.host + ":" + tv.port) {
            open(tv.name, tv.host, tv.port, tv.pin)
        })
        for (tv in found) box.addView(row(tv.name, getString(R.string.found), tv.host + ":" + tv.port) {
            open(tv.name, tv.host, tv.port, saved.firstOrNull { it.host == tv.host && it.port == tv.port }?.pin ?: "")
        })
    }

    private fun row(title: String, status: String, sub: String, click: () -> Unit): View {
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
        line.setOnClickListener { click() }
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
                if (address.isNotEmpty()) open(address, address, number, "")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun open(name: String, host: String, port: Int, pin: String) {
        if (pin.length == 6) {
            startActivity(SessionActivity.intent(this, name, host, port, pin))
            return
        }
        val field = EditText(this)
        field.hint = getString(R.string.pin_hint)
        AlertDialog.Builder(this)
            .setTitle(R.string.pin_hint)
            .setView(field)
            .setPositiveButton(R.string.enter) { _, _ ->
                val entered = field.text.toString().trim()
                Thread {
                    try {
                        TvClient(host, port, entered).list()
                        runOnUiThread {
                            try {
                                Books.factory(this).save(SavedTv("$host:$port", name, host, port, entered))
                            } catch (ignored: Exception) { }
                            startActivity(SessionActivity.intent(this, name, host, port, entered))
                        }
                    } catch (rejected: PinRejected) {
                        runOnUiThread { Toast.makeText(this, R.string.pin_wrong, Toast.LENGTH_SHORT).show() }
                    } catch (failed: Exception) {
                        runOnUiThread { Toast.makeText(this, R.string.failed, Toast.LENGTH_SHORT).show() }
                    }
                }.start()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

fun Activity.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
