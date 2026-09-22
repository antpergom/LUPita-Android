plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

// Plan, cache, presupuesto, colas, coste (F4). Kotlin PURO (TECHNICAL.md; ahora tambien lo hace cumplir
// :tools:boundaries): ninguna herramienta ni Room se conocen desde aqui, solo interfaces.
dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
