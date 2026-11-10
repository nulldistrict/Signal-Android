package org.thoughtcrime.securesms.crypto;


import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.crypto.KeyStoreHelper;
import org.thoughtcrime.securesms.keyvalue.PlainTextKeyValueStore;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * It can be rather expensive to read from the keystore, so this class caches the key in memory
 * after it is created.
 *
 * When a passphrase protects the database, the secret is only available after the passphrase has
 * been entered. It is wrapped with the passphrase-derived master secret in
 * {@link MasterSecretUtil} and the keystore-sealed copy is removed, so the passphrase is required
 * to open the database.
 */
public final class DatabaseSecretProvider {

  private static volatile DatabaseSecret instance;

  public static DatabaseSecret getOrCreateDatabaseSecret(@NonNull Context context) {
    if (instance == null) {
      synchronized (DatabaseSecretProvider.class) {
        if (instance == null) {
          instance = getOrCreate(context);
        }
      }
    }

    return instance;
  }

  private DatabaseSecretProvider() {
  }

  /**
   * Makes a passphrase-unwrapped database secret available after unlock. The secret has already
   * been authenticated by AES-GCM while unwrapping.
   */
  public static void setDatabaseSecretForPassphrase(@NonNull byte[] secret) {
    if (secret.length != 32) {
      throw new IllegalArgumentException("Unexpected database secret length");
    }

    synchronized (DatabaseSecretProvider.class) {
      instance = new DatabaseSecret(Arrays.copyOf(secret, secret.length));
    }
  }

  public static boolean isLockedByPassphrase(@NonNull Context context) {
    return MasterSecretUtil.isPassphraseRequired(context);
  }

  /**
   * Returns the database secret after a passphrase unlock. If the secret is not wrapped yet, the
   * install is being upgraded from a version where only the master secret was passphrase-protected;
   * in that case the existing keystore-sealed secret is wrapped and the sealed copy is removed.
   */
  public static @NonNull byte[] unlockDatabaseSecret(@NonNull Context context, @NonNull MasterSecret masterSecret)
      throws InvalidPassphraseException
  {
    if (MasterSecretUtil.hasWrappedDatabaseSecret(context)) {
      return MasterSecretUtil.unwrapDatabaseSecret(context, masterSecret);
    }

    DatabaseSecret databaseSecret = getExistingOrCreate(context);
    byte[]         secret         = databaseSecret.asBytes();

    MasterSecretUtil.wrapDatabaseSecret(context, masterSecret, secret);
    PlainTextKeyValueStore.setDatabaseEncryptedSecret(null);
    PlainTextKeyValueStore.setDatabaseLegacyUnencryptedSecret(null);

    synchronized (DatabaseSecretProvider.class) {
      instance = databaseSecret;
    }

    return Arrays.copyOf(secret, secret.length);
  }

  /**
   * Wraps the database secret with the given master secret and removes the keystore-sealed copy, so
   * that the passphrase is required to obtain the secret after the next start.
   */
  public static void enablePassphraseProtection(@NonNull Context context, @NonNull MasterSecret masterSecret) {
    DatabaseSecret databaseSecret = getOrCreateDatabaseSecret(context);

    MasterSecretUtil.wrapDatabaseSecret(context, masterSecret, databaseSecret.asBytes());

    PlainTextKeyValueStore.setDatabaseEncryptedSecret(null);
    PlainTextKeyValueStore.setDatabaseLegacyUnencryptedSecret(null);
  }

  /**
   * Restores the keystore-sealed database secret and removes the passphrase-wrapped copy, so that
   * the database can be opened again without a passphrase.
   */
  public static void disablePassphraseProtection(@NonNull Context context, @NonNull MasterSecret masterSecret)
      throws InvalidPassphraseException
  {
    byte[] secret = MasterSecretUtil.unwrapDatabaseSecret(context, masterSecret);

    try {
      KeyStoreHelper.SealedData sealed = KeyStoreHelper.seal(secret);

      PlainTextKeyValueStore.setDatabaseEncryptedSecret(sealed.serialize());
      PlainTextKeyValueStore.setDatabaseLegacyUnencryptedSecret(null);
    } finally {
      Arrays.fill(secret, (byte) 0);
    }

    MasterSecretUtil.clearDatabaseSecret(context);
  }

  private static @NonNull DatabaseSecret getOrCreate(@NonNull Context context) {
    if (MasterSecretUtil.isPassphraseRequired(context)) {
      throw new IllegalStateException("Database is locked by a passphrase");
    }

    return getExistingOrCreate(context);
  }

  private static @NonNull DatabaseSecret getExistingOrCreate(@NonNull Context context) {
    String unencryptedSecret = PlainTextKeyValueStore.getDatabaseLegacyUnencryptedSecret();
    String encryptedSecret   = PlainTextKeyValueStore.getDatabaseEncryptedSecret();

    if      (unencryptedSecret != null) return getUnencryptedDatabaseSecret(unencryptedSecret);
    else if (encryptedSecret != null)   return getEncryptedDatabaseSecret(encryptedSecret);
    else                                return createAndStoreDatabaseSecret();
  }

  private static @NonNull DatabaseSecret getUnencryptedDatabaseSecret(@NonNull String unencryptedSecret) {
    try {
      DatabaseSecret databaseSecret = new DatabaseSecret(unencryptedSecret);

      KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(databaseSecret.asBytes());

      PlainTextKeyValueStore.setDatabaseEncryptedSecret(encryptedSecret.serialize());
      PlainTextKeyValueStore.setDatabaseLegacyUnencryptedSecret(null);

      return databaseSecret;
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static @NonNull DatabaseSecret getEncryptedDatabaseSecret(@NonNull String serializedEncryptedSecret) {
    KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.SealedData.fromString(serializedEncryptedSecret);
    return new DatabaseSecret(KeyStoreHelper.unseal(encryptedSecret));
  }

  private static @NonNull DatabaseSecret createAndStoreDatabaseSecret() {
    SecureRandom random = new SecureRandom();
    byte[]       secret = new byte[32];
    random.nextBytes(secret);

    DatabaseSecret databaseSecret = new DatabaseSecret(secret);

    KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(databaseSecret.asBytes());
    PlainTextKeyValueStore.setDatabaseEncryptedSecret(encryptedSecret.serialize());

    return databaseSecret;
  }
}
