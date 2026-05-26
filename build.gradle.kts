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
        filters {
            excludes {
                classes(
                    "*.BuildConfig",
                    "*ComposableSingletons*",
                    "*_PreviewKt*",
                    "com.digitumdei.shotquill.AppKt*",
                    "*MainActivity*",
                    "*AndroidBrandProfileRepositoryFactory*",
                    "*EpochClock*",
                    "com.digitumdei.shotquill.media.MediaFileManager*",
                    "com.digitumdei.shotquill.media.MediaCaptureHandler*",
                    "com.digitumdei.shotquill.shared.db.*",
                )
            }
        }

        verify {
            rule {
                minBound(90)
            }
        }
    }
}
