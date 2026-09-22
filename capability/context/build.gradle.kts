plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

// Normalizacion determinista del arbol capturado (F2): filtrado, orden de lectura, roles, patron. Kotlin
// PURO: no conoce Android, UI, orquestador ni Room. Recibe el arbol ya filtrado por region (F1.3).
dependencies {
    implementation(project(":core:model"))

    testImplementation(libs.junit)
}
