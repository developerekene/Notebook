package com.example.core.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class AndroidKeystoreCryptoEngine(context: Context) : CryptoEngine {

    private val appContext = context.applicationContext
    private val secureRandom = SecureRandom()

    private val keyAlias = "notebook_vault_aes256_gcm_v1"
    private val androidKeyStoreProvider = "AndroidKeyStore"
    private val gcmTransformation = "AES/GCM/NoPadding"
    private val gcmTagLengthBits = 128
    private val pbkdf2Iterations = 100_000

    private val usingHardwareKeystore: Boolean by lazy {
        try {
            val ks = KeyStore.getInstance(androidKeyStoreProvider)
            ks.load(null)
            true
        } catch (_: Exception) {
            false
        }
    }

    override val algorithmName: String = "AES-256-GCM"
    override val keyStorageProvider: String
        get() = if (usingHardwareKeystore) "Android Keystore (Hardware-Backed)" else "Local Software Keystore"

    @Synchronized
    private fun getOrCreateMasterKey(): SecretKey {
        if (usingHardwareKeystore) {
            val keyStore = KeyStore.getInstance(androidKeyStoreProvider).apply { load(null) }
            val existing = keyStore.getKey(keyAlias, null) as? SecretKey
            if (existing != null) return existing

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, androidKeyStoreProvider)
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        } else {
            val prefs = appContext.getSharedPreferences("notebook_keystore_fallback", Context.MODE_PRIVATE)
            val stored = prefs.getString("fallback_key_b64", null)
            val keyBytes = if (stored != null) {
                Base64.decode(stored, Base64.NO_WRAP)
            } else {
                ByteArray(32).also {
                    secureRandom.nextBytes(it)
                    prefs.edit().putString("fallback_key_b64", Base64.encodeToString(it, Base64.NO_WRAP)).apply()
                }
            }
            return SecretKeySpec(keyBytes, "AES")
        }
    }

    override fun encrypt(plaintext: String): EncryptedPayload {
        val secretKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(gcmTransformation)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return EncryptedPayload(
            ciphertextBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    override fun decrypt(payload: EncryptedPayload): String {
        if (payload.ciphertextBase64.isBlank() || payload.ivBase64.isBlank()) return ""
        val secretKey = getOrCreateMasterKey()
        val iv = Base64.decode(payload.ivBase64, Base64.NO_WRAP)
        val cipherBytes = Base64.decode(payload.ciphertextBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(gcmTransformation)
        val spec = GCMParameterSpec(gcmTagLengthBits, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, StandardCharsets.UTF_8)
    }

    override fun encryptWithPassphrase(plaintext: String, passphrase: String): PassphraseEncryptedBundle {
        require(passphrase.length >= 4) { "Passphrase must be at least 4 characters." }
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val derivedKey = deriveAesKeyFromPassphrase(passphrase, salt, pbkdf2Iterations)

        val cipher = Cipher.getInstance(gcmTransformation)
        cipher.init(Cipher.ENCRYPT_MODE, derivedKey, GCMParameterSpec(gcmTagLengthBits, iv))
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

        return PassphraseEncryptedBundle(
            iterations = pbkdf2Iterations,
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            ciphertextBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        )
    }

    override fun decryptWithPassphrase(bundle: PassphraseEncryptedBundle, passphrase: String): String {
        val salt = Base64.decode(bundle.saltBase64, Base64.NO_WRAP)
        val iv = Base64.decode(bundle.ivBase64, Base64.NO_WRAP)
        val cipherBytes = Base64.decode(bundle.ciphertextBase64, Base64.NO_WRAP)
        val derivedKey = deriveAesKeyFromPassphrase(passphrase, salt, bundle.iterations)

        val cipher = Cipher.getInstance(gcmTransformation)
        cipher.init(Cipher.DECRYPT_MODE, derivedKey, GCMParameterSpec(gcmTagLengthBits, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, StandardCharsets.UTF_8)
    }

    private fun deriveAesKeyFromPassphrase(passphrase: String, salt: ByteArray, iterations: Int): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    override fun getKeyFingerprint(): String {
        val prefs = appContext.getSharedPreferences("notebook_vault_identity", Context.MODE_PRIVATE)
        val existing = prefs.getString("vault_fingerprint", null)
        if (existing != null) return existing

        // Derive a stable public vault fingerprint by encrypting a fixed domain tag once or hashing random entropy bound to install
        val entropy = ByteArray(32).also { secureRandom.nextBytes(it) }
        val digest = MessageDigest.getInstance("SHA-256").digest(entropy)
        val formatted = digest.take(8)
            .joinToString("") { "%02X".format(it) }
            .chunked(4)
            .joinToString(" · ")
        prefs.edit().putString("vault_fingerprint", formatted).apply()
        return formatted
    }

    override fun generateSaltBase64(): String {
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    override fun hashPin(pin: String, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pin.toCharArray(), salt, 50_000, 256)
        val bytes = factory.generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
