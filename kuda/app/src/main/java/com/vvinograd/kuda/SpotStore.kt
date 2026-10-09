package com.vvinograd.kuda

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Хранит места в SharedPreferences в виде JSON. */
class SpotStore(context: Context) {

    private val prefs = context.getSharedPreferences("spots", Context.MODE_PRIVATE)

    fun load(): MutableList<Spot> {
        val raw = prefs.getString(KEY, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Spot(o.getLong("id"), o.getString("name"), o.getDouble("lat"), o.getDouble("lon"), o.getLong("savedAt"))
            }
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun save(spots: List<Spot>) {
        val arr = JSONArray()
        spots.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("lat", it.lat)
                    .put("lon", it.lon)
                    .put("savedAt", it.savedAt),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    var selectedId: Long
        get() = prefs.getLong(KEY_SELECTED, -1L)
        set(value) = prefs.edit().putLong(KEY_SELECTED, value).apply()

    private companion object {
        const val KEY = "spots_json"
        const val KEY_SELECTED = "selected_id"
    }
}
