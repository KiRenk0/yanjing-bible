package org.yanjing.app.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale
import org.yanjing.app.R

/** Android resources remain the source of UI text, including background update messages. */
object I18n {
    @Volatile private var app: Context? = null
    private var cachedKey = ""
    private var cachedResources: Resources? = null
    fun initialize(context: Context) { app = context; synchronized(this) { cachedKey = ""; cachedResources = null } }
    fun choice(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales != null) return if (locales.isEmpty) "system" else canonical(locales[0])
        }
        return context.getSharedPreferences("yanjing", Context.MODE_PRIVATE).getString("uiLanguage", "system") ?: "system"
    }
    private fun canonical(locale: Locale): String = when {
        locale.language != "zh" -> "en"
        locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO") -> "zh-TW"
        else -> "zh-CN"
    }
    fun tag(context: Context): String = choice(context).let { if (it == "system") canonical(context.resources.configuration.locales[0]) else it }
    fun wrap(context: Context): Context {
        val locale = Locale.forLanguageTag(tag(context))
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(locale)); config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
    fun setLanguage(context: Context, tag: String) {
        require(tag in listOf("system", "en", "zh-CN", "zh-TW"))
        context.getSharedPreferences("yanjing", Context.MODE_PRIVATE).edit().putString("uiLanguage", tag).apply()
        synchronized(this) { cachedKey = ""; cachedResources = null }
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java)?.applicationLocales =
            if (tag == "system") LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }
    fun text(id: Int, vararg args: Any): String {
        val context = app
        if (context == null) {
            val fallback = I18nFallback.values[id] ?: ""
            return if (args.isEmpty()) fallback else String.format(Locale.SIMPLIFIED_CHINESE, fallback, *args)
        }
        val current = tag(context)
        val resources = synchronized(this) {
            if (current != cachedKey || cachedResources == null) { cachedKey = current; cachedResources = wrap(context).resources }
            cachedResources!!
        }
        return if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args)
    }
    fun bookName(book: Int): String = app?.let { context ->
        val resources = wrap(context).resources
        val index = resources.getStringArray(R.array.catalog_ids).indexOf(book.toString())
        resources.getStringArray(R.array.catalog_names).getOrNull(index)
    } ?: when {
        book in 1..66 -> org.yanjing.app.core.Books.names[book - 1]
        book == 67 -> "古兰经"
        book in 101..173 -> org.yanjing.app.core.vulgateBooks.firstOrNull { it.book == book }?.title.orEmpty()
        else -> org.yanjing.app.core.Classics.forBook(book)?.title.orEmpty()
    }
}

fun tr(id: Int, vararg args: Any): String = I18n.text(id, *args)
