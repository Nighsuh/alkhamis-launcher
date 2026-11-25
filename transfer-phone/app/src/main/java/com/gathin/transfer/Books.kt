package com.gathin.transfer

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONArray
import org.json.JSONObject

interface TvBook {
    fun list(): List<SavedTv>
    fun save(tv: SavedTv)
    fun forgetPin(host: String, port: Int)
}

/** Tests replace this so they never touch the keystore. */
object Books {
    var factory: (Context) -> TvBook = { EncryptedBook(it) }
}

class MemoryBook : TvBook {
    private val rows = ArrayList<SavedTv>()
    override fun list(): List<SavedTv> = rows.toList()
    override fun save(tv: SavedTv) {
        rows.removeAll { it.id == tv.id || (it.host == tv.host && it.port == tv.port) }
        rows.add(tv)
    }
    override fun forgetPin(host: String, port: Int) {
        val current = rows.toList()
        rows.clear()
        current.filter { it.host != host || it.port != port }.forEach { rows.add(it.copy(pin = "")) }
    }
}

class EncryptedBook(ctx: Context) : TvBook {
    private val prefs: SharedPreferences

    init {
        val master = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            "alkhamis-tvs",
            master,
            ctx,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun list(): List<SavedTv> {
        val raw = prefs.getString("tvs", "[]") ?: "[]"
        val array = JSONArray(raw)
        val out = ArrayList<SavedTv>(array.length())
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
            out.add(SavedTv(row.getString("id"), row.getString("name"), row.getString("host"),
                row.getInt("port"), row.optString("pin")))
        }
        return out
    }

    override fun save(tv: SavedTv) {
        val rows = list().filter { it.id != tv.id && !(it.host == tv.host && it.port == tv.port) } + tv
        write(rows)
    }

    override fun forgetPin(host: String, port: Int) {
        write(list().map { if (it.host == host && it.port == port) it.copy(pin = "") else it })
    }

    private fun write(rows: List<SavedTv>) {
        val array = JSONArray()
        for (tv in rows) {
            array.put(JSONObject()
                .put("id", tv.id)
                .put("name", tv.name)
                .put("host", tv.host)
                .put("port", tv.port)
                .put("pin", tv.pin))
        }
        prefs.edit().putString("tvs", array.toString()).apply()
    }
}
