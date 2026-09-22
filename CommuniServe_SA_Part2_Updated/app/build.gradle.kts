plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")

    // Firebase Authentication setup step 2 of 2: uncomment this line ONLY
    // after you have placed your own app/google-services.json (downloaded
    // from your Firebase project - applicationId za.co.communiservesa).
    // Applying this plugin without that file present makes Gradle fail
    // with "File google-services.json is missing", which is why it ships
    // commented out. See README "Firebase Authentication setup".
    // id("com.google.gms.google-services")
}

android {
    namespace = "za.co.communiservesa"
    compileSdk = 35

    defaultConfig {
        applicationId = "za.co.communiservesa"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3000/\"")
    }
    buildFeatures { buildConfig = true }

    // ADD THIS BACK!
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
    implementation("com.google.android.gms:play-services-location:21.3.0")
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

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}