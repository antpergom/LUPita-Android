plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.antoniopg.lupita.capability.screen"
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

// Fuente de pantalla real: servicio de accesibilidad (arbol filtrado por region + takeScreenshot). Es la
// UNICA capacidad que puede usar android.* (ver :tools:boundaries). No conoce UI, orquestador ni Room.
dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
