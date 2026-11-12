package com.quantumff.segue.feature.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The widget picker's previews. Below Android 15 the picker draws the provider info's static
 * `previewImage`; from Android 15 it draws the previews [WidgetUpdater] generates with Glance.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetPickerPreviewsTest {
    @Test
    fun `every widget declares a static preview below Android 15`() {
        for (widget in WIDGETS) {
            assertThat(providerInfo("xml", widget)).containsKey(PREVIEW_IMAGE)
        }
    }

    /**
     * Before the first publish, and after an app update clears them, the picker would fall back to
     * the static preview: the API 35 copies leave it out so the app icon shows as it always has.
     */
    @Test
    fun `Android 15 and later keep the generated previews`() {
        for (widget in WIDGETS) {
            val below35 = providerInfo("xml", widget)
            val from35 = providerInfo("xml-v35", widget)

            assertThat(from35).doesNotContainKey(PREVIEW_IMAGE)
            assertThat(from35).isEqualTo(below35 - PREVIEW_IMAGE)
        }
    }

    /** The static previews match the sizes the generated ones use (`previewSizeMode`). */
    @Test
    fun `the static previews are drawn at the generated previews' sizes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val density = context.resources.displayMetrics.density
        val sizes = mapOf(
            R.drawable.widget_strip_preview to (340 to 88),
            R.drawable.widget_card_preview to (340 to 160),
        )
        for ((preview, size) in sizes) {
            val drawable = checkNotNull(context.getDrawable(preview))
            val dp = Math.round(drawable.intrinsicWidth / density) to Math.round(drawable.intrinsicHeight / density)

            assertThat(dp).isEqualTo(size)
        }
    }

    private companion object {
        val WIDGETS = listOf("widget_strip_info", "widget_card_info")
        const val PREVIEW_IMAGE = "android:previewImage"

        /** The attributes of a provider info source file, by name, as written. */
        fun providerInfo(folder: String, name: String): Map<String, String> {
            val file = File("src/main/res/$folder/$name.xml")
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            return (0 until root.attributes.length).map { root.attributes.item(it) }
                .associate { it.nodeName to it.nodeValue }
        }
    }
}
