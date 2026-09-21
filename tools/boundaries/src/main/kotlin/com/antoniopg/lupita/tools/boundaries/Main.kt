package com.antoniopg.lupita.tools.boundaries

import java.io.File
import kotlin.system.exitProcess

/** Uso: `Main <raiz-del-proyecto>`. Sale con codigo 1 si hay alguna violacion. */
fun main(args: Array<String>) {
    val violations = check(File(args.firstOrNull() ?: "."))
    if (violations.isEmpty()) {
        println("boundaries: OK")
        return
    }
    // Formato estable "  :modulo: mensaje" -- scripts/verify.ps1 lo extrae del log.
    System.err.println("boundaries: ${violations.size} violacion(es)")
    violations.forEach { System.err.println("  ${it.module}: ${it.message}") }
    exitProcess(1)
}
