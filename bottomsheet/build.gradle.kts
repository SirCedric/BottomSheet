import com.vanniktech.maven.publish.DeploymentValidation
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    // Only the HTML flavour: with dokka-javadoc applied as well, the publishing plugin would pick
    // the javadoc format, which truncates every @param text after its first sentence.
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
}

group = "de.cedrickummer"
// The release workflow injects the real version. The SNAPSHOT fallback keeps a local publish
// recognisable and lets it run without a signing key.
version = providers.gradleProperty("releaseVersion").getOrElse("0.0.0-LOCAL-SNAPSHOT")

android {
    namespace = "de.cedrickummer.bottomsheet"
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

dokka {
    moduleName = "BottomSheet"
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true, validateDeployment = DeploymentValidation.PUBLISHED)
    signAllPublications()

    coordinates(artifactId = "bottomsheet")

    pom {
        name = "BottomSheet"
        description = "A bottom sheet for Jetpack Compose that attaches to any composable through a modifier " +
            "and behaves like SwiftUI's .sheet with presentationDetents."
        inceptionYear = "2026"
        url = "https://github.com/SirCedric/BottomSheet"
        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "SirCedric"
                name = "Cedric Kummer"
                url = "https://github.com/SirCedric"
            }
        }
        scm {
            url = "https://github.com/SirCedric/BottomSheet"
            connection = "scm:git:https://github.com/SirCedric/BottomSheet.git"
            developerConnection = "scm:git:ssh://git@github.com/SirCedric/BottomSheet.git"
        }
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
