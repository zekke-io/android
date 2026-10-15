plugins {
    alias(libs.plugins.android.library)

}

android {
    namespace = "zekke.android.platform"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.min.sdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        disable += listOf("AndroidGradlePluginVersion", "NewerVersionAvailable", "GradleDependency")
    }
}

dependencies {
    implementation(libs.mobile.base)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.sqldelight.android.driver)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.serialization.json)
}
