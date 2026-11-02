plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Las credenciales de Supabase (URL + clave publishable) pueden ir en el código: la clave
// publishable está pensada para el cliente; la seguridad la dan las políticas RLS de la base.
// El mapa (OpenStreetMap) no necesita ninguna clave.

android {
    namespace = "pe.edu.upn.recicausa"
    compileSdk = 35

    defaultConfig {
        applicationId = "pe.edu.upn.recicausa"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.5.0" // Sprint 5: Supabase (auth + REST), puntos de reciclaje, mapa OpenStreetMap y ubicación

        buildConfigField("String", "SUPABASE_URL", "\"https://bmtfohrwetfulwasaypv.supabase.co\"")
        buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_WptPiSisl5NlNb2H1RzPRQ_5gQJr8g2\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Base
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose + Material Design 3
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // MVVM: ViewModel + navegación
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Sprint 2: persistencia local
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // Recordatorios diarios
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Cámara (foto de lo que se deposita)
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Sprint 5: servicios web (Retrofit + OkHttp + kotlinx.serialization + Coroutines)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    // Sprint 5: mapa de OpenStreetMap (osmdroid, sin clave de API) y ubicación aproximada
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Pruebas unitarias
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
