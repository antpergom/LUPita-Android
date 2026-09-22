plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

// Idioma y extractores por reglas (F3), sobre texto ya normalizado (F2). Kotlin PURO: sin Android, sin
// modelo — si algun dia hace falta deteccion de idioma mas fina, sera OTRA capacidad, no esta.
dependencies {
    implementation(project(":core:model"))

    testImplementation(libs.junit)
}
