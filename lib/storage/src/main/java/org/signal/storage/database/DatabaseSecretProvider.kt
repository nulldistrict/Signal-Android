/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storage.database

import android.content.Context
import org.signal.core.util.crypto.KeyStoreHelper
import org.signal.storage.keyvalue.PlainTextKeyValueStore
import java.io.IOException
import java.security.SecureRandom

/**
 * It can be rather expensive to read from the keystore, so this class caches the key in memory
 * after it is created.
 */
object DatabaseSecretProvider {

  @Volatile
  private var instance: DatabaseSecret? = null

  /**
   * Fork: returns true while a passphrase-wrapped secret has replaced the keystore-sealed one. The secret must
   * then come from [setPassphraseUnwrappedSecret]; creating a new one here would make the database unreadable.
   */
  @Volatile
  @JvmStatic
  var isPassphraseWrapped: (Context) -> Boolean = { false }

  /** Fork: provides the secret unwrapped with the user's passphrase. */
  @JvmStatic
  fun setPassphraseUnwrappedSecret(secret: DatabaseSecret) {
    synchronized(this) {
      instance = secret
    }
  }

  @JvmStatic
  fun getOrCreateDatabaseSecret(context: Context): DatabaseSecret {
    if (instance == null) {
      synchronized(this) {
        if (instance == null) {
          check(!isPassphraseWrapped(context)) { "Database is locked by a passphrase" }
          instance = getOrCreate()
        }
      }
    }
    return instance!!
  }

  private fun getOrCreate(): DatabaseSecret {
    val unencryptedSecret = PlainTextKeyValueStore.databaseLegacyUnencryptedSecret
    val encryptedSecret = PlainTextKeyValueStore.databaseEncryptedSecret

    return when {
      unencryptedSecret != null -> getUnencryptedDatabaseSecret(unencryptedSecret)
      encryptedSecret != null -> getEncryptedDatabaseSecret(encryptedSecret)
      else -> createAndStoreDatabaseSecret()
    }
  }

  private fun getUnencryptedDatabaseSecret(unencryptedSecret: String): DatabaseSecret {
    try {
      val databaseSecret = DatabaseSecret.fromHex(unencryptedSecret)
      val encryptedSecret = KeyStoreHelper.seal(databaseSecret.asBytes())

      PlainTextKeyValueStore.databaseEncryptedSecret = encryptedSecret.serialize()
      PlainTextKeyValueStore.databaseLegacyUnencryptedSecret = null

      return databaseSecret
    } catch (e: IOException) {
      throw AssertionError(e)
    }
  }

  private fun getEncryptedDatabaseSecret(serializedEncryptedSecret: String): DatabaseSecret {
    val encryptedSecret = KeyStoreHelper.SealedData.fromString(serializedEncryptedSecret)
    return DatabaseSecret.fromBytes(KeyStoreHelper.unseal(encryptedSecret))
  }

  private fun createAndStoreDatabaseSecret(): DatabaseSecret {
    val secret = ByteArray(32)
    SecureRandom().nextBytes(secret)

    val databaseSecret = DatabaseSecret.fromBytes(secret)
    val encryptedSecret = KeyStoreHelper.seal(databaseSecret.asBytes())

    PlainTextKeyValueStore.databaseEncryptedSecret = encryptedSecret.serialize()

    return databaseSecret
  }
}
