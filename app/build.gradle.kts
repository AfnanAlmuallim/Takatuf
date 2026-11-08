plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

// Point debug builds at the Firebase Local Emulator Suite, e.g.
//   ./gradlew installDebug -Ptakatuf.emulatorHost=10.0.2.2
// (10.0.2.2 is the host machine as seen from the Android emulator.) Leave unset to use
// the real Firebase project from google-services.json.
val firebaseEmulatorHost: String = providers.gradleProperty("takatuf.emulatorHost").orNull.orEmpty()

// AI role suggestions call OpenAI straight from the app. The key and model come from the
// project's .env (git-ignored; see .env.example). Without a key the app hides the feature.
// Anything here ends up inside the APK, so give the key a spending cap.
val dotEnv: Map<String, String> = providers.fileContents(rootProject.layout.projectDirectory.file(".env"))
    .asText.orNull.orEmpty().lines()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
    .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }

fun quoted(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.z.takatuf"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.z.takatuf"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
        buildConfigField("String", "OPENAI_API_KEY", quoted(dotEnv["OPENAI_API_KEY"].orEmpty()))
        buildConfigField("String", "OPENAI_MODEL", quoted(dotEnv["OPENAI_MODEL"] ?: "gpt-6-luna"))
        buildConfigField("int", "AI_DAILY_LIMIT", (dotEnv["AI_DAILY_LIMIT"]?.toIntOrNull() ?: 20).toString())
    }

    buildTypes {
        debug {
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"$firebaseEmulatorHost\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    // Android's org.json is a stub in local unit tests; this is the real implementation.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
