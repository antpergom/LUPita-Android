plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

// Plan, cache, presupuesto, colas, coste (F4) + wiring real de "Analisis general" (F5 paso 3).
// Kotlin PURO (TECHNICAL.md; ahora tambien lo hace cumplir :tools:boundaries): ninguna herramienta
// concreta ni Room se conocen desde aqui, solo interfaces (:source:openai SI es conocido - es "la"
// fuente de esta primera burbuja, no una capacidad generica).
dependencies {
    implementation(project(":core:model"))
    implementation(project(":source:openai"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
