plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

// Fetch + readability + JSON-LD/OpenGraph (F3). Kotlin PURO: OkHttp y Jsoup son librerias JVM normales,
// no `android.*`. Sin red de pago todavia (eso es F4.5, y va detras de la cache del orquestador).
dependencies {
    implementation(project(":core:model"))
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
