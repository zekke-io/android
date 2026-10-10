package zekke.android.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties

@RunWith(AndroidJUnit4::class)
class KeystoreDeviceVaultTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alias = "zekke.test.device-record"
    private val other = "zekke.test.other-install"
    private val fileName = "test-device-record.bin"
    private val record = ByteArray(300) { (it * 7 + 1).toByte() }

    private fun vault(keyAlias: String = alias) = KeystoreDeviceVault(context, keyAlias, fileName)

    private fun stored() = File(context.noBackupFilesDir, fileName)

    @After
    fun forget() {
        vault().delete()
        vault(other).delete()
    }

    @Test
    fun storesReadsAndForgetsARecord() {
        assertNull(vault().load())
        vault().store(record)
        assertArrayEquals(record, vault().load())
        val onDisk = stored().readBytes()
        assertEquals(1 + 12 + record.size + 16, onDisk.size)
        assertFalse(onDisk.toList().windowed(record.size).any { it == record.toList() })
        vault().delete()
        assertNull(vault().load())
        assertFalse(stored().exists())
        assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias))
    }

    @Test
    fun aRewriteReplacesTheRecordUnderAFreshIv() {
        vault().store(record)
        val first = stored().readBytes()
        val second = ByteArray(40) { 9 }
        vault().store(second)
        assertArrayEquals(second, vault().load())
        assertFalse(first.copyOfRange(1, 13).contentEquals(stored().readBytes().copyOfRange(1, 13)))
    }

    @Test
    fun aRecordCopiedFromAnotherInstallDoesNotOpenAndIsForgotten() {
        vault(other).store(record)
        assertNull(vault().load())
        assertFalse(stored().exists())
    }

    @Test
    fun aTamperedRecordDoesNotOpen() {
        vault().store(record)
        val bytes = stored().readBytes()
        bytes[20] = (bytes[20] + 1).toByte()
        stored().writeBytes(bytes)
        assertNull(vault().load())
    }

    @Test
    fun theKeyNeverLeavesTheKeystore() {
        vault().store(record)
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null) as SecretKey
        assertNull(key.encoded)
        val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java) as KeyInfo
        assertTrue(vault().keyLocation != KeyLocation.NONE)
        android.util.Log.i("KeystoreDeviceVaultTest", "key location ${vault().keyLocation}")
        assertEquals(256, info.keySize)
        assertEquals(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT, info.purposes)
        assertFalse(info.isUserAuthenticationRequired)
    }

    private val lockStep: String? = InstrumentationRegistry.getArguments().getString("lockStep")
    private val lockAlias = "zekke.test.lock"
    private val lockFile = "test-lock-record.bin"

    private fun lockVault() = KeystoreDeviceVault(context, lockAlias, lockFile)

    @Test
    fun lockStepStore() {
        assumeTrue(lockStep == "store")
        lockVault().store(record)
    }

    @Test
    fun lockStepWhileLocked() {
        assumeTrue(lockStep == "locked")
        assertThrows(DeviceVaultUnavailableException::class.java) { lockVault().load() }
        assertTrue(File(context.noBackupFilesDir, lockFile).exists())
    }

    @Test
    fun lockStepAfterUnlock() {
        assumeTrue(lockStep == "unlocked")
        assertArrayEquals(record, lockVault().load())
        lockVault().delete()
    }
}
