plugins {
    alias(libs.plugins.android.library)
    // AGP 9 trae Kotlin integrado: no se aplica kotlin-android. El compilador de Compose si.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.antoniopg.lupita.ui.overlay"
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

dependencies {
    implementation(project(":core:model"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.lifecycle.runtime.ktx)
    // ViewModelStore/Owner: la ComposeView fuera de una Activity necesita aportar los tres owners.
    implementation(libs.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
}
