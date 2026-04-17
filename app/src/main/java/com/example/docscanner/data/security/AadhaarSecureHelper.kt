package com.example.docscanner.data.security

import android.content.Context
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AadhaarSecureHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val KEY_ALIAS     = "aadhaar_group_key"
        private const val KEYSTORE      = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LEN   = 128

        private const val SALT_PREF_FILE = "aadhaar_security"
        private const val SALT_PREF_KEY  = "install_salt_b64"
    }

    // ── Hashing (for group IDs) ───────────────────────────────────────────────
    // One-way: you can check equality but never recover the number.

    fun hashAadhaarNumber(digits12: String): String {
        require(digits12.length == 12 && digits12.all { it.isDigit() }) {
            "Expected 12-digit Aadhaar number"
        }
        val input = "${getOrCreateInstallSalt()}:$digits12".toByteArray(Charsets.UTF_8)
        val hash  = MessageDigest.getInstance("SHA-256").digest(input)
        return hash.joinToString("") { "%02x".format(it) }.take(24) // 96-bit prefix is plenty
    }

    fun hashLast4(digits12: String): String {
        val last4 = digits12.takeLast(4)
        val input = "${getOrCreateInstallSalt()}:last4:$last4".toByteArray(Charsets.UTF_8)
        val hash  = MessageDigest.getInstance("SHA-256").digest(input)
        return hash.joinToString("") { "%02x".format(it) }.take(16)
    }

    // ── Encryption (if you ever need to store the actual number) ─────────────
    // Returns Base64-encoded "iv:ciphertext".

    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv         = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined   = iv + cipherText
        return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        val combined   = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
        val iv         = combined.copyOfRange(0, 12)
        val cipherText = combined.copyOfRange(12, combined.size)
        val cipher     = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LEN, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // set true if you want biometric lock
                .build()
        )
        return keyGen.generateKey()
    }

    private fun getOrCreateInstallSalt(): String {
        val prefs = context.getSharedPreferences(SALT_PREF_FILE, Context.MODE_PRIVATE)
        val existing = prefs.getString(SALT_PREF_KEY, null)
        if (!existing.isNullOrBlank()) return existing

        val saltBytes = ByteArray(32)
        SecureRandom().nextBytes(saltBytes)
        val generated = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
        prefs.edit().putString(SALT_PREF_KEY, generated).apply()
        return generated
    }
}