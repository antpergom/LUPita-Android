plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.antoniopg.lupita.ui.theme"
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

    buildFeatures {
        compose = true
    }
}

// Sistema de diseno compartido (paleta y tema del mock). Es la UNICA excepcion a "un modulo UI no
// depende de otro modulo UI" (ver :tools:boundaries) y no depende de nada del proyecto.
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    testImplementation(libs.junit)
}
