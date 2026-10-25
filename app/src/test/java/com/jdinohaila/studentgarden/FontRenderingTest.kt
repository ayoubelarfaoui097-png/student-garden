package com.jdinohaila.studentgarden

import android.graphics.*
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = GardenApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontRenderingTest {
    private fun ink(resource: Int, text: String): Long {
        val app = ApplicationProvider.getApplicationContext<GardenApp>()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = ResourcesCompat.getFont(app, resource); textSize = 64f; color = Color.BLACK }
        val bitmap = Bitmap.createBitmap(1000, 150, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        Canvas(bitmap).drawText(text, 10f, 100f, paint)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.sumOf { 255L - Color.red(it) }
    }
    @Test fun latinAndArabicBoldAreVisiblyHeavierThanRegular() {
        assertTrue(ink(R.font.source_sans_bold, "Remember this lesson") > ink(R.font.source_sans_regular, "Remember this lesson") * 1.2)
        assertTrue(ink(R.font.noto_arabic_bold, "مراجعة الدروس اليوم") > ink(R.font.noto_arabic_regular, "مراجعة الدروس اليوم") * 1.2)
    }
}
