package dev.guilhermeluan.planner.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreSecretStoreTest {
    @Test
    fun tokenRoundTripsWithoutAppearingInPlainTextStorage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val suffix = UUID.randomUUID().toString()
        val alias = "planner-test-$suffix"
        val preferencesName = "planner-secrets-test-$suffix"
        val store = AndroidKeystoreSecretStore(context, alias, preferencesName)
        val token = "secret-session-token"

        try {
            store.writeToken(token)

            assertEquals(token, store.readToken())
            val persistedValues = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .all.values.joinToString()
            assertFalse(persistedValues.contains(token))

            store.clearToken()
            assertNull(store.readToken())
        } finally {
            context.deleteSharedPreferences(preferencesName)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }
}
