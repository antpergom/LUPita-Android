plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Frontera dura: este modulo es Kotlin PURO (sin android./androidx.). Lo comprueba
    // :tools:boundaries en cada `test`; si necesitas algo de Android, no va aqui.
    testImplementation(libs.junit)
}
