package org.yanjing.app.core

import org.json.JSONArray
import org.json.JSONObject

data class DevotionText(val en: String, val zhHans: String, val zhHant: String) {
    fun resolve(language: String): String = when (language) {
        "zh-CN", "zh-Hans" -> zhHans
        "zh-TW", "zh-Hant" -> zhHant
        else -> en
    }
}
data class Prayer(val id: String, val category: String, val title: DevotionText, val text: DevotionText)
data class RosaryMystery(val title: DevotionText, val reference: DevotionText, val meditation: DevotionText)
data class RosarySet(val id: String, val days: List<Int>, val title: DevotionText, val schedule: DevotionText, val mysteries: List<RosaryMystery>)
data class DevotionSource(val title: String, val url: String)
data class RosaryStep(val prayerID: String? = null, val decade: Int = 0, val repetition: Int = 0, val optional: Boolean = false)

data class DevotionCatalog(
    val edition: DevotionText, val guide: DevotionText, val prayers: List<Prayer>, val sets: List<RosarySet>,
    val sources: List<DevotionSource>, val opening: List<String>, val closing: List<String>
) {
    fun recommended(isoWeekday: Int): RosarySet = sets.first { isoWeekday in it.days }
    fun steps(set: RosarySet): List<RosaryStep> = buildList {
        opening.forEach { add(RosaryStep(it)) }
        set.mysteries.forEachIndexed { index, _ ->
            val decade = index + 1
            add(RosaryStep(decade = decade))
            add(RosaryStep("our_father", decade))
            repeat(10) { add(RosaryStep("hail_mary", decade, it + 1)) }
            add(RosaryStep("glory_be", decade))
            add(RosaryStep("fatima", decade, optional = true))
        }
        closing.forEach { add(RosaryStep(it, set.mysteries.size + 1)) }
    }

    companion object {
        fun parse(source: String): DevotionCatalog {
            val root = JSONObject(source)
            require(root.getInt("schema") == 1)
            fun text(obj: JSONObject) = DevotionText(obj.getString("en"), obj.getString("zhHans"), obj.getString("zhHant"))
            fun strings(array: JSONArray) = List(array.length()) { array.getString(it) }
            val prayers = root.getJSONArray("prayers").objects().map { Prayer(it.getString("id"), it.getString("category"), text(it.getJSONObject("title")), text(it.getJSONObject("text"))) }
            val sets = root.getJSONArray("sets").objects().map { obj ->
                val days = obj.getJSONArray("days")
                RosarySet(obj.getString("id"), List(days.length()) { days.getInt(it) }, text(obj.getJSONObject("title")), text(obj.getJSONObject("schedule")), obj.getJSONArray("mysteries").objects().map {
                    RosaryMystery(text(it.getJSONObject("title")), text(it.getJSONObject("reference")), text(it.getJSONObject("meditation")))
                })
            }
            require(prayers.map { it.id }.distinct().size == prayers.size && sets.all { it.mysteries.size == 5 })
            val catalog = DevotionCatalog(text(root.getJSONObject("edition")), text(root.getJSONObject("guide")), prayers, sets,
                root.getJSONArray("sources").objects().map { DevotionSource(it.getString("title"), it.getString("url")) }, strings(root.getJSONArray("opening")), strings(root.getJSONArray("closing")))
            require(sets.flatMap { it.days }.sorted() == (1..7).toList())
            require(sets.all { set -> catalog.steps(set).all { step -> step.prayerID == null || prayers.any { it.id == step.prayerID } } })
            return catalog
        }
        private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
    }
}
