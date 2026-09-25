package com.example.phoneguard.parent.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ParentTokenCipher {
  fun encrypt(value: String): String {
    require(value.isNotBlank()) { "value must not be blank." }

    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

    val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    val iv = cipher.iv
    require(iv.size in 1..255) { "Unexpected GCM IV size." }

    val payload = ByteArray(1 + iv.size + encrypted.size)
    payload[0] = iv.size.toByte()

    System.arraycopy(iv, 0, payload, 1, iv.size)
    System.arraycopy(encrypted, 0, payload, 1 + iv.size, encrypted.size)

    return Base64.encodeToString(
      payload,
      Base64.NO_WRAP or Base64.NO_PADDING,
    )
  }

  fun decrypt(payloadEncoded: String): String? =
    runCatching {
      val payload =
        Base64.decode(
          payloadEncoded,
          Base64.NO_WRAP or Base64.NO_PADDING,
        )

      require(payload.size > 2) { "Encrypted token payload is invalid." }

      val ivSize = payload[0].toInt() and 0xff
      require(ivSize > 0 && payload.size > 1 + ivSize) {
        "Encrypted token payload is invalid."
      }

      val iv = payload.copyOfRange(1, 1 + ivSize)
      val encrypted = payload.copyOfRange(1 + ivSize, payload.size)

      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(
        Cipher.DECRYPT_MODE,
        getOrCreateKey(),
        GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
      )

      String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

  fun deleteKey() {
    val keyStore =
      KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
      }

    if (keyStore.containsAlias(KEY_ALIAS)) {
      keyStore.deleteEntry(KEY_ALIAS)
    }
  }

  private fun getOrCreateKey(): SecretKey {
    val keyStore =
      KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
      }

    (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

    val keyGenerator =
      KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        KEYSTORE_PROVIDER,
      )

    keyGenerator.init(
      KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .build(),
    )

    return keyGenerator.generateKey()
  }

  private companion object {
    const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    const val KEY_ALIAS = "phone_guard_parent_control_token"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val GCM_TAG_LENGTH_BITS = 128
  }
}
