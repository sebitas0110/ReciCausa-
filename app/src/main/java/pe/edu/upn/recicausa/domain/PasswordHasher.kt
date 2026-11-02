package pe.edu.upn.recicausa.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Cifrado de contraseñas con PBKDF2-HMAC-SHA256 y sal aleatoria por usuario.
 * La contraseña nunca se guarda: solo el hash y la sal. Disponible desde Android 8.0 (API 26).
 */
object PasswordHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    data class Hashed(val hash: String, val salt: String)

    fun hash(password: String, salt: ByteArray = newSalt()): Hashed =
        Hashed(hash = encode(derive(password, salt)), salt = encode(salt))

    fun verify(password: String, hash: String, salt: String): Boolean {
        val expected = Base64.getDecoder().decode(hash)
        val actual = derive(password, Base64.getDecoder().decode(salt))
        return MessageDigest.isEqual(expected, actual) // comparación en tiempo constante
    }

    private fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
