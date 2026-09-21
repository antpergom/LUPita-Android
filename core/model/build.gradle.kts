plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Frontera dura: este modulo es Kotlin PURO (sin android./androidx.). Lo comprueba
    // :tools:boundaries en cada `test`; si necesitas algo de Android, no va aqui.
    // `api`: los contratos publicos exponen Flow.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
