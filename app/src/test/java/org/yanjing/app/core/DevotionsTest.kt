package org.yanjing.app.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.yanjing.app.data.DevotionRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35, 36])
class DevotionsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Test fun completeRosaryHasFiveDecadesAndAllPrayersInThreeLanguages() {
        val catalog = DevotionRepository(context).catalog
        assertEquals(12, catalog.prayers.size)
        assertEquals(4, catalog.sets.size)
        assertEquals(listOf("joyful", "sorrowful", "glorious", "luminous", "sorrowful", "joyful", "glorious"), (1..7).map { catalog.recommended(it).id })
        for (set in catalog.sets) {
            val steps = catalog.steps(set)
            assertEquals(80, steps.size)
            assertEquals(53, steps.count { it.prayerID == "hail_mary" })
            assertEquals(6, steps.count { it.prayerID == "our_father" })
            assertEquals(6, steps.count { it.prayerID == "glory_be" })
            assertEquals(5, steps.count { it.prayerID == null })
            assertEquals(5, steps.count { it.optional })
            for (decade in 1..5) assertEquals((1..10).toList(), steps.filter { it.decade == decade && it.prayerID == "hail_mary" }.map { it.repetition })
            assertEquals("sign_of_cross", steps.first().prayerID)
            assertEquals(listOf("hail_holy_queen", "rosary_closing", "sign_of_cross"), steps.takeLast(3).map { it.prayerID })
        }
        for (language in listOf("en", "zh-CN", "zh-TW")) {
            assertTrue(catalog.prayers.all { it.title.resolve(language).isNotBlank() && it.text.resolve(language).isNotBlank() })
            assertTrue(catalog.sets.all { it.mysteries.all { m -> m.meditation.resolve(language).isNotBlank() && m.reference.resolve(language).isNotBlank() } })
        }
        assertEquals("圣母经", catalog.prayers.first { it.id == "hail_mary" }.title.resolve("zh-CN"))
        assertEquals("聖母經", catalog.prayers.first { it.id == "hail_mary" }.title.resolve("zh-TW"))
    }
    @Test fun progressRestoresIndependentlyAndHandlesCompletionAndInvalidValues() {
        context.getSharedPreferences("devotions", Context.MODE_PRIVATE).edit().clear().commit()
        val repository = DevotionRepository(context)
        val joyful = repository.catalog.sets.first { it.id == "joyful" }
        val luminous = repository.catalog.sets.first { it.id == "luminous" }
        repository.position(joyful, 15)
        repository.position(luminous, 80)
        val restored = DevotionRepository(context)
        assertEquals(15, restored.position(joyful))
        assertEquals(80, restored.position(luminous))
        restored.position(joyful, -5)
        assertEquals(0, restored.position(joyful))
        restored.position(joyful, 999)
        assertEquals(80, restored.position(joyful))
        restored.position(joyful, 0)
        assertEquals(80, restored.position(luminous))
    }
}
