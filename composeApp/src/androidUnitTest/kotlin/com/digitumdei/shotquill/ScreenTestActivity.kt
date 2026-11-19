package com.digitumdei.shotquill

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Compose's test rule shows a screen inside an empty activity, and Robolectric
 * will only start an activity the app declares. Declaring that empty activity
 * here, for the length of a test, keeps it out of the app's own manifest, so
 * it is in neither the debug nor the release build.
 *
 * Use it ahead of the Compose rule:
 * ```
 * @get:Rule(order = 0) val screenTestActivity = ScreenTestActivity()
 * @get:Rule(order = 1) val compose = createComposeRule()
 * ```
 */
class ScreenTestActivity : ExternalResource() {
    override fun before() {
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application.packageManager).addOrUpdateActivity(
            ActivityInfo().apply {
                name = ComponentActivity::class.java.name
                packageName = application.packageName
            },
        )
    }
}
