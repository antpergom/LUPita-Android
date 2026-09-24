package com.antoniopg.lupita.core.model

import kotlinx.coroutines.flow.Flow

/**
 * La clave de API del proveedor de IA (F5, hoy solo DeepSeek: decision 2026-09-22, "Proveedor por
 * defecto: DeepSeek"). Nunca se vuelve a mostrar en claro una vez guardada (escritura, no lectura,
 * igual que cualquier campo de credencial de este tipo de app) — [AiCredentialsRepository.credentials]
 * solo dice SI hay una clave guardada, `apiKey` no se expone hacia la UI.
 */
data class AiCredentials(val apiKey: String)

/** Contrato del guardado. La implementacion (Tink sobre DataStore) vive en `:app`. */
interface AiCredentialsRepository {
    val credentials: Flow<AiCredentials?>

    suspend fun save(credentials: AiCredentials)
    suspend fun clear()
}
