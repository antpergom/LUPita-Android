plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.antoniopg.lupita.capability.ocr"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}

// OCR en el dispositivo (F3): ML Kit Text Recognition, modelo local, sin red por peticion. Desviacion
// deliberada del plan original (TECHNICAL.md lo marcaba "sin Android"): un OCR JVM puro necesitaria
// binarios nativos pesados (tipo Tesseract) sin encajar bien en Android; ML Kit es la via real. Excepcion
// declarada en :tools:boundaries junto a :capability:screen.
dependencies {
    implementation(project(":core:model"))
    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
