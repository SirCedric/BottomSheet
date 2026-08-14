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
        // JUnit5 fuer die reinen JVM-Tests. Instrumentiertes JUnit5 braeuchte ein
        // Drittanbieter-Plugin mit experimenteller Unterstuetzung — dort bleibt es bei JUnit4.
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

// explicitApi() gilt sonst auch fuer die Testquellen; dort exponieren Testmethoden
// zwangslaeufig interne Typen der Naht.
tasks.withType<KotlinCompile>().configureEach {
    if (name.contains("Test")) {
        compilerOptions.freeCompilerArgs.add("-Xexplicit-api=disable")
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.foundation)
    api(libs.compose.ui)

    // Nur fuer den Back-Handler; die Public API exponiert keine Activity-Typen.
    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testImplementation(libs.assertk)
    testRuntimeOnly(libs.junit5.engine)
    // Gradle 9 verlangt den Launcher explizit auf dem Test-Runtime-Classpath.
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // ui-test-junit4 zieht Espresso 3.5 mit; das ruft eine API, die es ab Android 17 nicht
    // mehr gibt (InputManager.getInstance).
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.assertk)
    debugImplementation(libs.compose.ui.test.manifest)
}
