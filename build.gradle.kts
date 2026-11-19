plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kover)
}

dependencies {
    kover(project(":shared"))
    kover(project(":composeApp"))
}

// Kover's verify and report tasks must not overlap. With parallel builds the
// root task could run at the same moment as the same task in :shared and
// :composeApp, and it then sometimes measured coverage without the exclusions
// below: the gate reported about 55% where the real figure is over 90%. It
// showed up on a fresh checkout, where all three have work to do, which is
// every CI run. Making the root tasks wait for the others removes the overlap.
val koverMeasuring = Regex("kover(Cached)?Verify.*|kover(Xml|Html|Binary|Log)Report.*")
tasks.matching { koverMeasuring.matches(it.name) }.configureEach {
    mustRunAfter(subprojects.map { project -> project.tasks.matching { koverMeasuring.matches(it.name) } })
}

kover {
    reports {
        // Applied to BOTH the generated reports (reports.filters) and the total/verify
        // scope (total.filters). In Kover 0.9.x the common reports.filters are not
        // honoured by total.verify, so without the explicit total.filters the gate
        // measured unfiltered coverage (generated SQLDelight code, Android-only
        // classes, Compose screens) and reported far below the real figure.
        val koverExclusions = listOf(
            "*.BuildConfig",
            "*ComposableSingletons*",
            "*_PreviewKt*",
            "com.digitumdei.shotquill.AppKt*",
            "com.digitumdei.shotquill.AppScreen",
            "*MainActivity*",
            "com.digitumdei.shotquill.clipboard.AndroidClipboardWriter*",
            "com.digitumdei.shotquill.share.AndroidPostShareLauncher*",
            "com.digitumdei.shotquill.media.MediaCaptureHandler*",
            "com.digitumdei.shotquill.media.MediaCaptureHandlerKt*",
            "com.digitumdei.shotquill.media.ContentResolverMediaImporter*",
            "com.digitumdei.shotquill.media.LogoImporter*",
            "com.digitumdei.shotquill.image.AndroidPhotoRenderer*",
            "com.digitumdei.shotquill.image.SvgLogo*",
            "com.digitumdei.shotquill.speech.Android*",
            "com.digitumdei.shotquill.social.FirebaseImageHost*",
            "com.digitumdei.shotquill.screen.HomeScreenKt*",
            "com.digitumdei.shotquill.screen.PostEditorScreenKt*",
            "com.digitumdei.shotquill.screen.SettingsScreenKt*",
            "com.digitumdei.shotquill.ui.*",
            "com.digitumdei.shotquill.shared.db.*",
            "com.digitumdei.shotquill.shared.ai.UrlConnectionAiHttpTransport*",
            "com.digitumdei.shotquill.shared.social.UrlConnectionHttpTransport*",
        )

        filters {
            excludes {
                classes(*koverExclusions.toTypedArray())
            }
        }

        total {
            filters {
                excludes {
                    classes(*koverExclusions.toTypedArray())
                }
            }
            verify {
                rule {
                    minBound(80)
                }
            }
        }
    }
}
