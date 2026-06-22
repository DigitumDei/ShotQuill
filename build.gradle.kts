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
            "*AndroidBrandProfileRepositoryFactory*",
            "com.digitumdei.shotquill.screen.ManualPostDraftWorkspaceScreenKt",
            "com.digitumdei.shotquill.screen.NewPostScreenKt",
            "com.digitumdei.shotquill.screen.FinalPostComposerScreenKt",
            "com.digitumdei.shotquill.shared.db.*",
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
