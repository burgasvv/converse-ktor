package org.burgas.encryption

import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CipherManager {

    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val SECRET_KEY = "12345678901234567890123456789012"
    private const val IV = "1234567890123456"

    fun encrypt(plainText: String): String {
        val keySpec = SecretKeySpec(SECRET_KEY.toByteArray(), "AES")
        val ivSpec = IvParameterSpec(IV.toByteArray())

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

        val encryptedBytes = cipher.doFinal(plainText.toByteArray())
        return Base64.getEncoder().encodeToString(encryptedBytes)
    }

    fun decrypt(encryptedText: String, secretKey: String, iv: String): String {
        val keySpec = SecretKeySpec(secretKey.toByteArray(), "AES")
        val ivSpec = IvParameterSpec(iv.toByteArray())

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

        val decodedBytes = Base64.getDecoder().decode(encryptedText)
        val decryptedBytes = cipher.doFinal(decodedBytes)
        return String(decryptedBytes)
    }
}