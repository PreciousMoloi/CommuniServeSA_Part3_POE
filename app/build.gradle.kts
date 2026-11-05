plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")

}

android {
    namespace = "za.co.communiservesa"
    compileSdk = 36

    defaultConfig {
        applicationId = "za.co.communiservesa"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Debug default: the emulator's alias for the developer PC. Overridden for release below.
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3000/\"")
        // Deployed (HTTPS) API - what a physical phone uses by default. Override with RELEASE_API_URL.
        val hostedUrl = (project.findProperty("RELEASE_API_URL") as String?)
            ?: System.getenv("RELEASE_API_URL")
            ?: "https://communiservesa-api.onrender.com/"
        buildConfigField("String", "HOSTED_API_URL", "\"$hostedUrl\"")
        manifestPlaceholders["usesCleartext"] = "true"
    }
    buildFeatures { buildConfig = true }

    // Release signing is read from environment variables so no keystore or password
    // is ever committed to GitHub. See docs/PLAY_STORE_PUBLISHING.md.
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Production traffic must be HTTPS (set RELEASE_API_URL in gradle.properties or the environment).
            val releaseUrl = (project.findProperty("RELEASE_API_URL") as String?)
                ?: System.getenv("RELEASE_API_URL")
                ?: "https://communiservesa-api.onrender.com/"
            buildConfigField("String", "API_BASE_URL", "\"$releaseUrl\"")
            manifestPlaceholders["usesCleartext"] = "false"
            isMinifyEnabled = false
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // OpenStreetMap map view - works without a Google Maps API key.
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // Firebase BoM keeps every com.google.firebase artifact on a single,
    // known-compatible set of versions - the individual firebase-* lines
    // below intentionally have no version number of their own.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-messaging")
    // Lets FirebaseAuthManager use Task.await() to call Firebase from a
    // coroutine instead of nested addOnCompleteListener callbacks.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    // Native "Sign in with Google" account chooser (Credential Manager) -> Firebase credential.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

// Firebase (Google SSO + push) switches itself on when app/google-services.json exists.
// Without that file (for example on GitHub Actions) the app still builds and runs:
// every Firebase call is guarded and the features simply report "not configured".
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
