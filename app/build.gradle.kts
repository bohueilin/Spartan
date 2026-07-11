import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.kover")
}

// Room schema history for migration tests (docs/Spartan_Enhancements.md §1).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.spartan"
    compileSdk = 35

    // Secrets/config are read from local.properties (gitignored) or the environment. They default
    // to blank + mock mode, so Spartan builds and runs with NO credentials committed. See .env.example.
    val localProps = Properties()
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { localProps.load(it) }
    fun cfg(key: String, default: String = ""): String =
        localProps.getProperty(key) ?: System.getenv(key) ?: default
    fun strField(value: String) = "\"" + value.replace("\"", "\\\"") + "\""

    defaultConfig {
        applicationId = "com.spartan"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // AppAuth (OAuth redirect) scheme. Deliberately DISTINCT from the spartan:// deep-link
        // scheme: AppAuth's RedirectUriReceiverActivity filters on the whole scheme (no host),
        // which would collide with spartan://today|connections.
        manifestPlaceholders["appAuthRedirectScheme"] = "com.spartan.oauth"

        // Feature flags — default true => mock WHOOP + stub Calendar (no network, no credentials).
        buildConfigField("boolean", "USE_MOCK_WHOOP", cfg("SPARTAN_USE_MOCK_WHOOP", "true"))
        buildConfigField("boolean", "USE_MOCK_CALENDAR", cfg("SPARTAN_USE_MOCK_CALENDAR", "true"))
        // Health Connect as a WHOOP alternative — adapter landed, OFF until manifest health
        // permissions + Play Health-apps declaration ship (see docs/Spartan_Enhancements.md §3).
        buildConfigField("boolean", "USE_HEALTH_CONNECT", cfg("SPARTAN_USE_HEALTH_CONNECT", "false"))
        // WHOOP OAuth (blank unless configured).
        buildConfigField("String", "WHOOP_CLIENT_ID", strField(cfg("WHOOP_CLIENT_ID")))
        buildConfigField("String", "WHOOP_CLIENT_SECRET", strField(cfg("WHOOP_CLIENT_SECRET")))
        buildConfigField("String", "WHOOP_REDIRECT_URI", strField(cfg("WHOOP_REDIRECT_URI", "com.spartan.oauth://whoop")))
        buildConfigField("String", "WHOOP_AUTH_URL", strField(cfg("WHOOP_AUTH_URL", "https://api.prod.whoop.com/oauth/oauth2/auth")))
        buildConfigField("String", "WHOOP_TOKEN_URL", strField(cfg("WHOOP_TOKEN_URL", "https://api.prod.whoop.com/oauth/oauth2/token")))
        buildConfigField("String", "WHOOP_API_BASE_URL", strField(cfg("WHOOP_API_BASE_URL", "https://api.prod.whoop.com/developer")))
        // Google Calendar OAuth (blank unless configured).
        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_ID", strField(cfg("GOOGLE_OAUTH_CLIENT_ID")))
        buildConfigField("String", "GOOGLE_OAUTH_REDIRECT_URI", strField(cfg("GOOGLE_OAUTH_REDIRECT_URI", "com.spartan.oauth://google")))
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            // Play-ready: shrink + obfuscate. Signing is configured locally per
            // docs/RELEASE_CHECKLIST.md (never committed); unsigned artifacts still build.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // Robolectric: run Android-dependent unit tests (WorkManager, DataStore) on the JVM.
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        // Expose committed Room schema history to the instrumentation migration test.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.dagger:hilt-android:2.60.1")
    implementation("androidx.hilt:hilt-work:1.2.0") // @HiltWorker for DailyPlanRefreshWorker
    implementation("com.google.android.play:review-ktx:2.0.2") // in-app review prompt
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07") // HC adapter (flag-gated off)
    implementation("androidx.glance:glance-appwidget:1.1.1") // home-screen "next activity" widget

    // Phase 2 — real integrations. Unused in the default mock build; enabled behind USE_MOCK_* flags.
    implementation("androidx.security:security-crypto:1.1.0-alpha06") // Keystore-backed EncryptedSharedPreferences
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("net.openid:appauth:0.11.1") // OAuth 2.0 authorization-code + PKCE

    ksp("androidx.room:room-compiler:2.6.1")
    ksp("com.google.dagger:hilt-android-compiler:2.60.1")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // JVM-run Android tests (no emulator): WorkManager, DataStore, notification logic.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.work:work-testing:2.10.0")

    // Instrumentation (device/emulator; compiled in CI, run in the emulator job).
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
