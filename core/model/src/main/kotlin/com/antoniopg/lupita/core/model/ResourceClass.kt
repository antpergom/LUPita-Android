package com.antoniopg.lupita.core.model

/**
 * Como clasifica una capacidad el orquestador (F4), por lo cara/lenta que es: cada clase tiene su propia
 * cola con su propio limite de paralelismo. Ver docs/decisions/2026-09-21-arquitectura-por-capas-y-artefactos.md.
 */
enum class ResourceClass {
    /** Local, gratis, rapida (regex, hash...): puede ir bastante en paralelo. */
    DETERMINISTIC,

    /** Local, con un modelo (OCR...): mas cara que determinista, sigue sin salir del dispositivo. */
    ML_LOCAL,

    /** Sale del dispositivo pero no es un LLM (fetch web, ClaimReview...). */
    NETWORK,

    /** Un proveedor de IA: la mas cara y la mas limitada en paralelismo (limites de tasa del proveedor). */
    LLM,
}
