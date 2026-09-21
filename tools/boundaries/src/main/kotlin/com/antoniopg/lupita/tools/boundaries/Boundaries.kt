package com.antoniopg.lupita.tools.boundaries

import java.io.File

/** Tipo de modulo, deducido de su ruta Gradle. Manda sobre que puede depender de que. */
enum class Kind { APP, UI, ORCHESTRATOR, CAPABILITY, SOURCE, CORE, TOOLS }

data class Violation(val module: String, val message: String)

/** Dependencias entre modulos permitidas por tipo. Todo lo que no este aqui esta prohibido. */
private val ALLOWED_DEPS: Map<Kind, Set<Kind>> = mapOf(
    Kind.APP to setOf(Kind.UI, Kind.ORCHESTRATOR, Kind.CAPABILITY, Kind.SOURCE, Kind.CORE),
    Kind.UI to setOf(Kind.CORE, Kind.ORCHESTRATOR),
    Kind.ORCHESTRATOR to setOf(Kind.CAPABILITY, Kind.SOURCE, Kind.CORE),
    // Una capacidad no conoce a otra capacidad ni a nada por encima: es lo que la hace extraible.
    Kind.CAPABILITY to setOf(Kind.CORE),
    Kind.SOURCE to setOf(Kind.CORE),
    Kind.CORE to setOf(Kind.CORE),
    Kind.TOOLS to emptySet(),
)

/** Tipos que deben ser Kotlin puro: sin `android.` ni `androidx.`. */
private val PURE_KINDS = setOf(Kind.CORE, Kind.CAPABILITY, Kind.SOURCE)

/** Excepciones declaradas: modulos cuyo trabajo ES tocar Android (accesibilidad y captura). */
private val ANDROID_ALLOWED = setOf(":capability:screen")

/**
 * Sistema de diseno compartido: el UNICO modulo UI del que otros modulos UI pueden depender. Sin esta
 * excepcion cada modulo UI tendria que duplicar la paleta.
 */
private const val SHARED_THEME = ":ui:theme"

/** El modelo base no depende de ningun otro modulo. */
private val NO_DEPS = setOf(":core:model")

private val ANDROID_IMPORT = Regex("""^\s*import\s+(android|androidx)\.""")
private val PROJECT_DEP = Regex("""project\(\s*"(:[^"]+)"\s*\)""")
private val INCLUDE = Regex("""include\(([^)]*)\)""")
private val QUOTED_PATH = Regex(""""(:[^"]+)"""")

fun kindOf(path: String): Kind? = when {
    path == ":app" -> Kind.APP
    path.startsWith(":ui:") -> Kind.UI
    path == ":orchestrator" -> Kind.ORCHESTRATOR
    path.startsWith(":capability:") -> Kind.CAPABILITY
    path.startsWith(":source:") -> Kind.SOURCE
    path.startsWith(":core:") -> Kind.CORE
    path.startsWith(":tools:") -> Kind.TOOLS
    else -> null
}

fun parseModules(settings: String): List<String> =
    INCLUDE.findAll(settings).flatMap { QUOTED_PATH.findAll(it.groupValues[1]) }
        .map { it.groupValues[1] }.toList()

fun parseProjectDeps(buildScript: String): List<String> =
    PROJECT_DEP.findAll(buildScript).map { it.groupValues[1] }.distinct().toList()

private fun moduleDir(root: File, path: String) = File(root, path.trim(':').replace(':', '/'))

/** Comprueba todos los modulos declarados en settings.gradle.kts. Lista vacia = todo en orden. */
fun check(root: File): List<Violation> {
    val settings = File(root, "settings.gradle.kts")
    if (!settings.isFile) return listOf(Violation("(raiz)", "no existe settings.gradle.kts en $root"))

    val violations = mutableListOf<Violation>()
    for (module in parseModules(settings.readText())) {
        // Fallo en cerrado: un modulo sin clasificar no se deja pasar en silencio.
        val kind = kindOf(module)
        if (kind == null) {
            violations += Violation(module, "modulo sin clasificar: anade su tipo en Boundaries.kt")
            continue
        }
        val dir = moduleDir(root, module)

        val script = File(dir, "build.gradle.kts")
        val deps = if (script.isFile) parseProjectDeps(script.readText()) else emptyList()
        if (module in NO_DEPS && deps.isNotEmpty()) {
            violations += Violation(module, "no puede depender de ningun modulo (depende de ${deps.joinToString()})")
        }
        for (dep in deps) {
            val depKind = kindOf(dep)
            when {
                depKind == null -> violations += Violation(module, "depende de $dep, que no esta clasificado")
                depKind !in ALLOWED_DEPS.getValue(kind) && !(kind == Kind.UI && dep == SHARED_THEME) ->
                    violations += Violation(module, "un modulo $kind no puede depender de $dep ($depKind)")
            }
        }

        if (kind in PURE_KINDS && module !in ANDROID_ALLOWED) {
            violations += androidImports(dir).map { (where, line) ->
                Violation(module, "importa Android ($where): $line")
            }
        }
    }
    return violations
}

/** Imports de `android.`/`androidx.` en `src/`, ignorando cualquier carpeta `build` (codigo generado). */
private fun androidImports(moduleDir: File): List<Pair<String, String>> {
    val src = File(moduleDir, "src")
    if (!src.isDirectory) return emptyList()
    return src.walkTopDown()
        .onEnter { it.name != "build" }
        .filter { it.isFile && it.extension == "kt" }
        .flatMap { file ->
            file.readLines().withIndex()
                .filter { ANDROID_IMPORT.containsMatchIn(it.value) }
                .map { "${file.name}:${it.index + 1}" to it.value.trim() }
        }.toList()
}
