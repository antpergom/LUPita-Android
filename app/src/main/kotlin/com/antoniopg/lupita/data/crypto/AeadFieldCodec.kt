package com.antoniopg.lupita.data.crypto

import com.google.crypto.tink.Aead
import java.util.Base64

/**
 * Cifra/descifra un valor con el primitivo [Aead] de Tink, atando el ciphertext al nombre de su campo
 * como datos asociados (AAD) — mismo patron que `app-android-rrss-publisher` (proyecto hermano),
 * reutilizado tal cual: intercambiar el ciphertext guardado de un campo por el de otro falla el
 * descifrado en vez de "colar" silenciosamente el valor equivocado.
 *
 * Codificado en Base64 para poder guardarse como `String` en una clave de Preferences DataStore.
 */
class AeadFieldCodec(private val aead: Aead) {
    fun encrypt(fieldName: String, plaintext: String): String {
        val ciphertext = aead.encrypt(plaintext.toByteArray(Charsets.UTF_8), aad(fieldName))
        return Base64.getEncoder().encodeToString(ciphertext)
    }

    /** `null` ante cualquier fallo (AAD que no casa, ciphertext corrupto, Base64 invalido) — nunca
     * lanza, para que un campo danado no tumbe la lectura del resto. */
    fun decrypt(fieldName: String, base64Ciphertext: String): String? = runCatching {
        val ciphertext = Base64.getDecoder().decode(base64Ciphertext)
        String(aead.decrypt(ciphertext, aad(fieldName)), Charsets.UTF_8)
    }.getOrNull()

    private fun aad(fieldName: String): ByteArray = fieldName.toByteArray(Charsets.UTF_8)
}
