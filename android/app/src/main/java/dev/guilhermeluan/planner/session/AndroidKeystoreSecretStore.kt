package dev.guilhermeluan.planner.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreSecretStore(
    context: Context,
    private val keyAlias: String = "planner-session-key",
    preferencesName: String = "planner-protected-session",
) : SessionSecretStore {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    override suspend fun writeToken(token: String) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        check(
            preferences.edit()
                .putString(TOKEN_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString(TOKEN_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .commit(),
        ) { "Não foi possível proteger a sessão no dispositivo" }
    }

    override suspend fun readToken(): String? = withContext(Dispatchers.IO) {
        val encodedIv = preferences.getString(TOKEN_IV, null) ?: return@withContext null
        val encodedCiphertext = preferences.getString(TOKEN_CIPHERTEXT, null)
            ?: return@withContext null
        val key = keyStore().getKey(keyAlias, null) as? SecretKey ?: return@withContext null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP)),
            )
        }
        val plaintext = cipher.doFinal(Base64.decode(encodedCiphertext, Base64.NO_WRAP))
        plaintext.toString(Charsets.UTF_8)
    }

    override suspend fun clearToken() = withContext(Dispatchers.IO) {
        preferences.edit().remove(TOKEN_IV).remove(TOKEN_CIPHERTEXT).commit()
        Unit
    }

    private fun getOrCreateKey(): SecretKey {
        val existing = keyStore().getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TOKEN_IV = "token_iv"
        const val TOKEN_CIPHERTEXT = "token_ciphertext"
    }
}
