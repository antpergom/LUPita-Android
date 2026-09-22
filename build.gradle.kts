plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

// Requisitos de build que scripts/verify.ps1 da por hechos (ver
// docs/decisions/2026-09-21-script-de-verificacion-bajo-consumo.md):
subprojects {
    // Filtro de tests por propiedad (`-PlupitaTests=*.FooTest,*.BarTest`) en vez de `--tests`: en los
    // modulos Android `test` es una tarea de ciclo de vida (no de tipo Test) y Gradle rechaza
    // `--tests` si ALGUNA tarea con ese nombre no lo soporta. Comprobado con el primer build real.
    val testPatterns = providers.gradleProperty("lupitaTests")

    tasks.withType<Test>().configureEach {
        // Un patron que solo coincide en un modulo NO debe hacer fallar a los demas.
        filter.isFailOnNoMatchingTests = false
        testPatterns.orNull?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.forEach { filter.includeTestsMatching(it) }
    }

    // Los tests unitarios de Android corren solo en debug: evita ejecutarlos dos veces.
    tasks.matching { it.name == "testReleaseUnitTest" }.configureEach { enabled = false }

    // Cualquier `test` comprueba antes las fronteras entre modulos: la modularidad es una condicion
    // del build, no una promesa.
    if (path != ":tools:boundaries") {
        tasks.matching { it.name == "test" }.configureEach {
            dependsOn(":tools:boundaries:checkBoundaries")
        }
    }
}
