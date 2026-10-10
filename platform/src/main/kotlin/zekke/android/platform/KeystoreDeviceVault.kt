package zekke.android.platform

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.AtomicFile
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import zekke.core.device.DeviceVault

class DeviceVaultUnavailableException(cause: Throwable) :
    IllegalStateException("the device record cannot be used right now: the phone may be locked", cause)

enum class KeyLocation { STRONGBOX, TRUSTED_ENVIRONMENT, SECURE_HARDWARE, SOFTWARE, NONE }

class KeystoreDeviceVault(
    private val context: Context,
    private val keyAlias: String = KEY_ALIAS,
    fileName: String = RECORD_FILE,
) : DeviceVault {
    private val file = AtomicFile(File(context.noBackupFilesDir, fileName))

    val keyLocation: KeyLocation
        get() {
            val key = keyStore().getKey(keyAlias, null) as? SecretKey ?: return KeyLocation.NONE
            val info = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE).getKeySpec(key, KeyInfo::class.java) as KeyInfo
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (info.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeyLocation.STRONGBOX
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeyLocation.TRUSTED_ENVIRONMENT
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeyLocation.SOFTWARE
                    else -> KeyLocation.SECURE_HARDWARE
                }
            } else {
                @Suppress("DEPRECATION")
                if (info.isInsideSecureHardware) KeyLocation.SECURE_HARDWARE else KeyLocation.SOFTWARE
            }
        }

    override fun store(record: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val sealed = try {
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.doFinal(record)
        } catch (error: GeneralSecurityException) {
            throw DeviceVaultUnavailableException(error)
        }
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "the Keystore chose a ${iv.size}-byte IV" }
        val stream = file.startWrite()
        try {
            stream.write(byteArrayOf(FORMAT_VERSION))
            stream.write(iv)
            stream.write(sealed)
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }

    override fun load(): ByteArray? {
        if (!file.baseFile.exists()) return null
        val stored = file.readFully()
        val key = keyStore().getKey(keyAlias, null) as? SecretKey
        if (key == null || stored.size <= 1 + IV_BYTES || stored[0] != FORMAT_VERSION) return forgetUnreadable()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, stored, 1, IV_BYTES))
        } catch (_: KeyPermanentlyInvalidatedException) {
            return forgetUnreadable()
        } catch (error: GeneralSecurityException) {
            throw DeviceVaultUnavailableException(error)
        }
        return try {
            cipher.doFinal(stored, 1 + IV_BYTES, stored.size - 1 - IV_BYTES)
        } catch (_: AEADBadTagException) {
            forgetUnreadable()
        } catch (error: GeneralSecurityException) {
            throw DeviceVaultUnavailableException(error)
        }
    }

    override fun delete() {
        file.delete()
        keyStore().takeIf { it.containsAlias(keyAlias) }?.deleteEntry(keyAlias)
    }

    private fun forgetUnreadable(): ByteArray? {
        file.delete()
        return null
    }

    private fun lockScreenIsSecure(): Boolean = context.getSystemService(KeyguardManager::class.java).isDeviceSecure

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun key(): SecretKey = (keyStore().getKey(keyAlias, null) as? SecretKey) ?: generateKey()

    private fun generateKey(): SecretKey {
        val strongBox = context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        if (strongBox) {
            try {
                return generateKey(strongBox = true)
            } catch (_: StrongBoxUnavailableException) {
            }
        }
        return generateKey(strongBox = false)
    }

    private fun generateKey(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .setRandomizedEncryptionRequired(true)
            .setUnlockedDeviceRequired(lockScreenIsSecure())
            .setIsStrongBoxBacked(strongBox)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "zekke.device-record"
        const val RECORD_FILE = "device-record.bin"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val FORMAT_VERSION: Byte = 0x01
    }
}
