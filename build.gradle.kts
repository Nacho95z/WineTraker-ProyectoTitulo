// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false

    // Plugin de Google Services
    id("com.google.gms.google-services") version "4.4.2" apply false

    // Plugin de Crashlytics (Kotlin DSL)
    id("com.google.firebase.crashlytics") version "3.0.2" apply false

    // Add the dependency for the Performance Monitoring Gradle plugin
    id("com.google.firebase.firebase-perf") version "2.0.2" apply false
}
