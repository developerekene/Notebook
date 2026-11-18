package com.example.core.crypto

import kotlinx.serialization.Serializable

@Serializable
data class EncryptedPayload(
    val ciphertextBase64: String,
    val ivBase64: String
)

interface CryptoEngine {
    val algorithmName: String
    val keyStorageProvider: String

    fun encrypt(plaintext: String): EncryptedPayload
    fun decrypt(payload: EncryptedPayload): String

    fun encryptWithPassphrase(plaintext: String, passphrase: String): PassphraseEncryptedBundle
    fun decryptWithPassphrase(bundle: PassphraseEncryptedBundle, passphrase: String): String

    fun getKeyFingerprint(): String
    fun hashPin(pin: String, saltBase64: String): String
    fun generateSaltBase64(): String
}

@Serializable
data class PassphraseEncryptedBundle(
    val version: Int = 1,
    val algorithm: String = "AES-256-GCM/PBKDF2-HMAC-SHA256",
    val iterations: Int = 100_000,
    val saltBase64: String,
    val ivBase64: String,
    val ciphertextBase64: String
)
