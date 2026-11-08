package org.yanjing.app.data

import android.content.Context
import org.yanjing.app.core.DevotionCatalog
import org.yanjing.app.core.RosarySet

class DevotionRepository(private val context: Context) {
    val catalog: DevotionCatalog by lazy { context.assets.open("devotions/catalog.json").bufferedReader(Charsets.UTF_8).use { DevotionCatalog.parse(it.readText()) } }
    private val preferences = context.getSharedPreferences("devotions", Context.MODE_PRIVATE)
    fun position(set: RosarySet): Int = preferences.getInt("position.${set.id}", 0).coerceIn(0, catalog.steps(set).size)
    fun position(set: RosarySet, value: Int) {
        preferences.edit().putInt("position.${set.id}", value.coerceIn(0, catalog.steps(set).size)).apply()
    }
}
