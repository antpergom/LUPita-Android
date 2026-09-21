plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

// Capacidad PURA (sin Android): la puerta de privacidad por app y el redactor de datos sensibles. No
// conoce UI, orquestador ni Room; recibe la configuracion como parametro. Extraible a otro proyecto.
dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
