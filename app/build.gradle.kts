import java.util.Properties

plugins {
    alias(libs.plugins.android.application)

    // Add the Google services Gradle plugin
    id("com.google.gms.google-services")

    // Plugin Crashlytics
    id("com.google.firebase.crashlytics")

    // Add the Performance Monitoring Gradle plugin
    id("com.google.firebase.firebase-perf")
}

android {
    namespace = "com.example.winertraker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.winertraker"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 🔐 Leer OPENAI_API_KEY desde local.properties
        val localProperties = Properties()
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) {
            localFile.inputStream().use { localProperties.load(it) }
        }

        val openAiKey = localProperties.getProperty("OPENAI_API_KEY") ?: ""
        buildConfigField("String", "OPENAI_API_KEY", "\"$openAiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }


    buildFeatures {
        buildConfig = true
    }
}

val camerax_version = "1.2.2"

dependencies {
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.firebase:firebase-messaging:25.0.1")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.transport.api)
    implementation(libs.transport.api)
    implementation(libs.transport.api)
    implementation(libs.transport.api)

    //Pinch-to-zoom
    implementation ("com.github.chrisbanes:PhotoView:2.3.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // 🔥 Firebase BoM (controla versiones de todos los Firebase)
    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))

    // Add the dependency for the Performance Monitoring library
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation("com.google.firebase:firebase-perf")

    // Crashlytics + Analytics (SIN versión)
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")

    // 🔔 Cloud Messaging
    implementation("com.google.firebase:firebase-messaging")

    // Auth (ya no necesitas poner la versión)
    implementation("com.google.firebase:firebase-auth")

    // Firestore y Storage (también sin versión, usando BoM)
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")

    // CameraX – puedes dejar solo esta tanda (no repetir abajo)
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")
    implementation("androidx.camera:camera-extensions:$camerax_version")

    // ML Kit
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:object-detection:17.0.0")
    implementation("com.google.mlkit:image-labeling:17.0.7")

    // Picasso
    implementation("com.squareup.picasso:picasso:2.71828")

    // GIF
    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.25")
}


//dependencies {
//    implementation ("com.github.PhilJay:MPAndroidChart:v3.1.0")
//
//    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
//    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
//
//    implementation ("com.google.firebase:firebase-storage:20.0.0")
//    implementation("androidx.camera:camera-core:$camerax_version")
//    implementation ("androidx.camera:camera-camera2:$camerax_version")
//    implementation ("androidx.camera:camera-lifecycle:$camerax_version")
//    implementation ("androidx.camera:camera-view:$camerax_version")
//    implementation ("androidx.camera:camera-extensions:$camerax_version")
//    implementation(libs.appcompat)
//    implementation(libs.material)
//    implementation(libs.activity)
//    implementation(libs.constraintlayout)
//    testImplementation(libs.junit)
//    androidTestImplementation(libs.ext.junit)
//    androidTestImplementation(libs.espresso.core)
//    // Import the BoM for the Firebase platform
//    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))
//
//    // Add the dependencies for the Crashlytics and Analytics libraries
//    // When using the BoM, you don't specify versions in Firebase library dependencies
//    implementation("com.google.firebase:firebase-crashlytics")
//    implementation("com.google.firebase:firebase-analytics")
//
//    // Add the dependency for the Firebase Authentication library
//    // TODO: Add the dependencies for Firebase products you want to use
//    // When using the BoM, don't specify versions in Firebase dependencies
//    implementation("com.google.firebase:firebase-analytics")
//    implementation("com.google.firebase:firebase-auth:23.1.0")
//    // Also add the dependency for the Google Play services library and specify its version
//    implementation("com.google.android.gms:play-services-auth:21.2.0")
//    // Add the dependencies for any other desired Firebase products
//    // https://firebase.google.com/docs/android/setup#available-libraries
//    // Java language implementation
//    implementation("androidx.biometric:biometric:1.1.0")
//// CameraX
//    implementation ("androidx.camera:camera-core:1.2.2")
//    implementation ("androidx.camera:camera-camera2:1.2.2")
//    implementation ("androidx.camera:camera-lifecycle:1.2.2")
//    implementation ("androidx.camera:camera-view:1.2.2")
//    implementation ("com.google.mlkit:object-detection:17.0.0")
//
//// ML Kit para OCR
//    implementation ("com.google.mlkit:text-recognition:16.0.0")
//
//    implementation ("com.google.firebase:firebase-firestore:24.7.1")
//
//    // Picasso for image loading
//    implementation ("com.squareup.picasso:picasso:2.71828")
//
//
//    implementation("com.google.mlkit:image-labeling:17.0.7")
//
//    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.25")
//
//
//}