plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

// EXIF, huella perceptual (pHash/dHash) y (mas adelante) C2PA (F3). Kotlin PURO: parsea bytes que ya
// llegan hechos (ver RegionImage en :capability:screen); no toca `android.graphics.Bitmap` para nada.
dependencies {
    implementation(project(":core:model"))

    testImplementation(libs.junit)
}
