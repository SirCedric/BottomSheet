import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.sircedric.bottomsheet"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // JUnit5 for the plain JVM tests. Instrumented JUnit5 would need a third-party plugin
        // whose instrumentation support is experimental, so device tests stay on JUnit4.
        unitTests.all { it.useJUnitPlatform() }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }
}

kotlin {
    explicitApi()
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())
    }
}

// explicitApi() would otherwise apply to the test sources as well, where test methods
// unavoidably expose the internal types of the seam.
tasks.withType<KotlinCompile>().configureEach {
    if (name.contains("Test")) {
        compilerOptions.freeCompilerArgs.add("-Xexplicit-api=disable")
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.foundation)
    api(libs.compose.ui)

    // Only for the back handler; the public API exposes no activity types.
    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testImplementation(libs.assertk)
    testRuntimeOnly(libs.junit5.engine)
    // Gradle 9 requires the launcher explicitly on the test runtime classpath.
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // ui-test-junit4 pulls in Espresso 3.5, which calls an API that no longer exists from
    // Android 17 on (InputManager.getInstance).
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.assertk)
    debugImplementation(libs.compose.ui.test.manifest)
}
