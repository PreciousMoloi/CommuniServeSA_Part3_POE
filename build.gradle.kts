plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false

    // Applied from app/build.gradle.kts only when app/google-services.json is present.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
