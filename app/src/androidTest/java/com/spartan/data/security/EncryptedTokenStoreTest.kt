package com.spartan.data.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Needs the real Android Keystore, which Robolectric lacks, so this runs on a device/emulator. */
@RunWith(AndroidJUnit4::class)
class EncryptedTokenStoreTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = EncryptedTokenStore(context)

    @Test
    fun saveLoadClearAndClearAll_andNothingIsPlaintextOnDisk() {
        store.save(SecureTokenStore.WHOOP_ACCESS, "plaintext-whoop-token")
        store.save(SecureTokenStore.GOOGLE_REFRESH, "plaintext-google-token")
        assertEquals("plaintext-whoop-token", store.load(SecureTokenStore.WHOOP_ACCESS))
        val raw = context.getSharedPreferences("spartan_secure_tokens", Context.MODE_PRIVATE).all
        assertTrue(raw.values.none { it.toString().contains("plaintext") })

        store.clear(SecureTokenStore.WHOOP_ACCESS)
        assertNull(store.load(SecureTokenStore.WHOOP_ACCESS))
        assertEquals("plaintext-google-token", store.load(SecureTokenStore.GOOGLE_REFRESH))

        store.clearAll()
        assertNull(store.load(SecureTokenStore.GOOGLE_REFRESH))
    }
}
