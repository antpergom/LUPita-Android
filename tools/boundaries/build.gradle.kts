plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

// Comprueba las fronteras entre modulos (dependencias permitidas + pureza de Android). Los demas
// modulos hacen depender su `test` de esta tarea (ver build.gradle.kts raiz).
val boundariesMarker = layout.buildDirectory.file("boundaries.ok")

tasks.register<JavaExec>("checkBoundaries") {
    group = "verification"
    description = "Falla si un modulo depende de otro que tiene prohibido, o importa Android donde no debe."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.antoniopg.lupita.tools.boundaries.MainKt")
    args(rootProject.projectDir.absolutePath)
    // Solo se re-ejecuta si cambia algun script de build o fuente Kotlin.
    inputs.files(
        rootProject.fileTree(rootProject.projectDir) {
            include("settings.gradle.kts", "**/build.gradle.kts", "**/src/**/*.kt")
            exclude("**/build/**", ".gradle/**", ".git/**", "fixtures/**")
        },
    )
    outputs.file(boundariesMarker)
    val marker = boundariesMarker
    doLast { marker.get().asFile.writeText("ok") }
}
