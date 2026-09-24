plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

// Cliente de OpenAI (GPT-6 Luna, F5) - Kotlin PURO: OkHttp y kotlinx.serialization son librerias JVM
// normales, no `android.*`. Sin cablear al orquestador todavia (BudgetEnforcer/ResourceQueues, F4).
dependencies {
    implementation(project(":core:model"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
