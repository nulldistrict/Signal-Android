/*
 * Ported from Molly IM (https://github.com/mollyim/mollyim-android).
 * Licensed under the GNU General Public License v3.0 or later.
 */
package org.thoughtcrime.securesms.crypto;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.crypto.KeyStoreHelper;
import org.signal.storage.database.DatabaseSecret;
import org.signal.storage.database.DatabaseSecretProvider;
import org.signal.storage.keyvalue.PlainTextKeyValueStore;

import java.util.Arrays;

/**
 * Protects the SQLCipher database secret with the user's passphrase.
 * <p>
 * While a passphrase is set, the secret is wrapped with the passphrase-derived master secret in
 * {@link MasterSecretUtil} and the keystore-sealed copy kept by upstream's
 * {@link DatabaseSecretProvider} is removed, so the passphrase is required to open the database.
 * <p>
 * {@link DatabaseSecretProvider} stays upstream's implementation; this class only feeds it the
 * unwrapped secret after unlock and stops it from creating a new secret while the real one is
 * wrapped. Note that {@link DatabaseSecret} does not copy its bytes, so the bytes handed to it or
 * read from it must never be cleared.
 */
public final class PassphraseDatabaseSecret {

  private static final int SECRET_LENGTH = 32;

  private PassphraseDatabaseSecret() {
  }

  /**
   * Must run in Application#onCreate before anything can ask for the database secret.
   */
  public static void install() {
    DatabaseSecretProvider.setPassphraseWrapped(context -> MasterSecretUtil.hasWrappedDatabaseSecret(context));
  }

  /**
   * Makes a passphrase-unwrapped database secret available after unlock. The secret has already
   * been authenticated by AES-GCM while unwrapping. The caller may clear {@code secret} afterwards.
   */
  public static void provideUnwrappedSecret(@NonNull byte[] secret) {
    if (secret.length != SECRET_LENGTH) {
      throw new IllegalArgumentException("Unexpected database secret length");
    }

    DatabaseSecretProvider.setPassphraseUnwrappedSecret(DatabaseSecret.fromBytes(Arrays.copyOf(secret, secret.length)));
  }

  /**
   * Returns a copy of the database secret after a passphrase unlock. If the secret is not wrapped
   * yet, the install is being upgraded from a version where only the master secret was
   * passphrase-protected; in that case the existing keystore-sealed secret is wrapped and the sealed
   * copy is removed.
   */
  public static @NonNull byte[] unlockDatabaseSecret(@NonNull Context context, @NonNull MasterSecret masterSecret)
      throws InvalidPassphraseException
  {
    if (MasterSecretUtil.hasWrappedDatabaseSecret(context)) {
      return MasterSecretUtil.unwrapDatabaseSecret(context, masterSecret);
    }

    byte[] secret = DatabaseSecretProvider.getOrCreateDatabaseSecret(context).asBytes();

    MasterSecretUtil.wrapDatabaseSecret(context, masterSecret, secret);
    clearKeystoreSealedSecret();

    return Arrays.copyOf(secret, secret.length);
  }

  /**
   * Wraps the database secret with the given master secret and removes the keystore-sealed copy, so
   * that the passphrase is required to obtain the secret after the next start.
   */
  public static void enablePassphraseProtection(@NonNull Context context, @NonNull MasterSecret masterSecret) {
    byte[] secret = DatabaseSecretProvider.getOrCreateDatabaseSecret(context).asBytes();

    MasterSecretUtil.wrapDatabaseSecret(context, masterSecret, secret);
    clearKeystoreSealedSecret();
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

  private static void clearKeystoreSealedSecret() {
    PlainTextKeyValueStore.setDatabaseEncryptedSecret(null);
    PlainTextKeyValueStore.setDatabaseLegacyUnencryptedSecret(null);
  }
}
