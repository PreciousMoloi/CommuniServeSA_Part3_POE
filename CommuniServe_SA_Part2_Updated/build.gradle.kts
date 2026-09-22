plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false

    // Firebase Authentication setup step 1 of 2: this plugin reads
    // app/google-services.json and wires the values into the app so
    // FirebaseAuth.getInstance() works at runtime. Left OFF ("apply false"
    // only declares the version, it never applies it here) so the project
    // builds immediately without a real Firebase project. Once you add
    // your own google-services.json to app/, uncomment the matching line
    // in app/build.gradle.kts to switch it on - see README "Firebase
    // Authentication setup".
    id("com.google.gms.google-services") version "4.4.2" apply false
}
