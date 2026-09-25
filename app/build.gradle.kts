plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.antoniopg.lupita"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.antoniopg.lupita"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // Ofuscar NO protege secretos compilados (R8 no cifra constantes); por eso esta app no
            // compila ninguno. Solo sube el listón frente a una inspeccion casual del DEX.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room genera el esquema como JSON versionado (F4 paso 3, primera BD del proyecto) — permite detectar
// una migracion olvidada en revision de codigo, igual que hace el proyecto hermano.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":orchestrator"))
    implementation(project(":source:openai"))
    implementation(project(":capability:privacy"))
    implementation(project(":capability:screen"))
    implementation(project(":capability:context"))
    implementation(project(":capability:text"))
    implementation(project(":capability:image"))
    implementation(project(":capability:web"))
    implementation(project(":capability:ocr"))
    implementation(project(":ui:overlay"))
    implementation(project(":ui:app"))
    implementation(project(":ui:theme"))

    implementation(libs.core.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.datastore.preferences)
    // Dispatchers.Main del scope del servicio; sin este artefacto falla en ejecucion.
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    // F5 paso 1: credenciales del proveedor de IA cifradas (mismo mecanismo que el proyecto hermano).
    implementation(libs.tink.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
}
