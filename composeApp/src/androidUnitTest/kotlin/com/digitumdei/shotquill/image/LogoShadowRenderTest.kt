package com.digitumdei.shotquill.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.digitumdei.shotquill.shared.domain.LogoCorner
import com.digitumdei.shotquill.shared.domain.LogoSettings
import com.digitumdei.shotquill.shared.domain.LookSettings
import com.digitumdei.shotquill.shared.domain.PhotoTreatment
import java.io.File
import java.io.FileOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * Renders a real image, with real graphics, and looks at its pixels: a mid-grey
 * photo with a white square as the logo, once with the shadow and once without.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LogoShadowRenderTest {
    private val filesDir: File = RuntimeEnvironment.getApplication().filesDir
    private val renderer = AndroidPhotoRenderer(filesDir)

    // A 4:5 photo larger than the finished image, so that comes out at 1080 by 1350 with nothing cropped off.
    private val photo = File(filesDir, "grey.jpg").also { file ->
        val bitmap = Bitmap.createBitmap(1200, 1500, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(128, 128, 128))
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    }

    // A white square in the middle of a transparent picture: clear edges to measure from.
    private val logoFile = File(filesDir, "square.png").also { file ->
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(20f, 20f, 80f, 80f, Paint().apply { color = Color.WHITE })
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun render(shadow: Float, opacity: Float = 1f): Bitmap {
        val outcome = renderer.render(
            RenderRequest(
                sourceUri = "file://${photo.absolutePath}",
                treatment = PhotoTreatment(lookEnabled = false),
                look = LookSettings(),
                logo = LogoSettings(
                    uri = "file://${logoFile.absolutePath}",
                    widthFraction = 0.5f,
                    opacity = opacity,
                    marginFraction = 0.04f,
                    corner = LogoCorner.BottomEnd,
                    shadow = shadow,
                ),
                outputName = "shadow-$shadow-$opacity",
            ),
        )
        assertTrue(outcome is RenderOutcome.Success, "render failed: $outcome")
        val file = File((outcome as RenderOutcome.Success).uri.removePrefix("file://"))
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun brightness(bitmap: Bitmap, x: Int, y: Int): Int {
        val pixel = bitmap.getPixel(x, y)
        return (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
    }

    // The logo is 540 wide with its corner at (497, 767); its white square runs
    // from (605, 875) to (929, 1199). The shadow is set 2.7 pixels down and right.
    private val insideSquare = 760 to 1030
    // Measured beside the middle of an edge, where a blurred shadow is at its
    // fullest; at a corner it thins out to a quarter.
    private val justOutsideBottomRight = 760 to 1201
    private val justOutsideTopLeft = 760 to 872
    private val farAway = 150 to 150

    @Test
    fun theShadowDarkensThePhotoJustBelowAndRightOfTheLogoAndNowhereElse() {
        val plain = render(shadow = 0f)
        val shadowed = render(shadow = 1f)
        assertEquals(1080 to 1350, shadowed.width to shadowed.height)

        // Without a shadow the photo beside the logo is untouched.
        assertEquals(128, brightness(plain, justOutsideBottomRight.first, justOutsideBottomRight.second), 6)
        // With one, it is clearly darker there...
        val beside = brightness(shadowed, justOutsideBottomRight.first, justOutsideBottomRight.second)
        assertTrue(beside < 100, "expected a shadow below the logo, brightness was $beside")
        // ...the logo itself is still white on top of it...
        assertTrue(brightness(shadowed, insideSquare.first, insideSquare.second) > 245)
        // ...the side the shadow falls away from is barely touched...
        val upLeft = brightness(shadowed, justOutsideTopLeft.first, justOutsideTopLeft.second)
        assertTrue(upLeft > beside + 20, "the shadow should fall down and right: up-left $upLeft, down-right $beside")
        // ...and the rest of the photo is not touched at all.
        assertEquals(128, brightness(shadowed, farAway.first, farAway.second), 6)
    }

    @Test
    fun aLighterShadowDarkensLessAndAFainterLogoHasAFainterShadow() {
        val full = brightness(render(shadow = 1f), justOutsideBottomRight.first, justOutsideBottomRight.second)
        val half = brightness(render(shadow = 0.5f), justOutsideBottomRight.first, justOutsideBottomRight.second)
        val fullFaintLogo = brightness(
            render(shadow = 1f, opacity = 0.4f),
            justOutsideBottomRight.first,
            justOutsideBottomRight.second,
        )

        assertTrue(half > full + 10, "half strength should be lighter: half $half, full $full")
        assertTrue(half < 122, "half strength should still show: $half")
        assertTrue(fullFaintLogo > full + 10, "a fainter logo should cast a fainter shadow: $fullFaintLogo against $full")
    }

    @Test
    fun theShadowNeverShowsThroughALogoThatIsSlightlySeeThrough() {
        // At 90% strength a little of the photo shows through the logo. With
        // the shadow on, it must still be the photo that shows, not the shadow.
        val plain = brightness(render(shadow = 0f, opacity = 0.9f), insideSquare.first, insideSquare.second)
        val shadowed = brightness(render(shadow = 1f, opacity = 0.9f), insideSquare.first, insideSquare.second)

        assertEquals(plain, shadowed, 3)
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) {
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "expected about $expected, was $actual")
    }
}
