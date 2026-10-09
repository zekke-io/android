plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "zekke.android.feature.onboarding"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.min.sdk.get().toInt()
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        disable += listOf("AndroidGradlePluginVersion", "NewerVersionAvailable", "GradleDependency")
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(libs.mobile.base)
}
