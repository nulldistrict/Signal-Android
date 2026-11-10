/**
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.crypto;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.Base64;
import org.signal.core.util.Util;
import org.signal.core.util.crypto.KeyStoreHelper;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.ecc.ECPrivateKey;
import org.signal.libsignal.protocol.ecc.ECPublicKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Helper class for generating and securely storing a MasterSecret.
 *
 * The MasterSecret is protected with an Argon2id-derived key (ported from Molly). When the
 * passphrase also protects the database secret, the derivation is additionally bound to a
 * non-exportable keystore HMAC key, so copying the preferences file alone is not enough to run an
 * offline brute-force attack.
 *
 * @author Moxie Marlinspike
 */
public class MasterSecretUtil {

  private static final String TAG = Log.tag(MasterSecretUtil.class);

  public static final String UNENCRYPTED_PASSPHRASE = "unencrypted";
  public static final String PREFERENCES_NAME       = "SecureSMS-Preferences";

  private static final String KEY_ALIAS_DEFAULT = "MollySecret";

  private static final String ASYMMETRIC_LOCAL_PUBLIC_DJB  = "asymmetric_master_secret_curve25519_public";
  private static final String ASYMMETRIC_LOCAL_PRIVATE_DJB = "asymmetric_master_secret_curve25519_private";

  private static final String PREF_PASSPHRASE_INITIALIZED = "passphrase_initialized";
  private static final String PREF_PASSPHRASE_REQUIRED    = "passphrase_required";
  private static final String PREF_PASSPHRASE_SALT        = "passphrase_salt";
  private static final String PREF_KDF_PARAMETERS         = "kdf_parameters";
  private static final String PREF_KDF_ELAPSED            = "kdf_elapsed";
  private static final String PREF_ENCRYPTION_IV          = "encryption_iv";
  private static final String PREF_MASTER_SECRET          = "master_secret";
  private static final String PREF_KEYSTORE_INITIALIZED   = "keystore_initialized";
  private static final String PREF_KEYSTORE_ALIAS         = "keystore_alias";
  private static final String PREF_DB_SECRET_WRAPPED      = "db_secret_wrapped";

  public static MasterSecret changeMasterSecretPassphrase(Context context,
                                                          MasterSecret masterSecret,
                                                          String newPassphrase)
  {
    SharedPreferences.Editor prefs = getSharedPreferences(context).edit();

    String          keyStoreAlias = null;
    String          savedKeyStoreAlias = retrieve(context, PREF_KEYSTORE_ALIAS, KEY_ALIAS_DEFAULT);
    SecureSecretKeySpec secretKey;

    if (isUnencryptedPassphrase(newPassphrase)) {
      secretKey = getUnencryptedKey();
    } else {
      PassphraseBasedKdf kdf = new PassphraseBasedKdf();

      System.gc();

      kdf.findParameters(getMaxKdfMemory(context));

      keyStoreAlias = UUID.randomUUID().toString();
      kdf.setHmacKey(KeyStoreHelper.createKeyStoreEntryHmac(keyStoreAlias, hasStrongBox(context)));

      byte[] passphraseSalt = generateSalt();

      prefs.putString(PREF_PASSPHRASE_SALT, Base64.encodeWithPadding(passphraseSalt));
      prefs.putString(PREF_KDF_PARAMETERS, kdf.getParameters());
      prefs.putLong(PREF_KDF_ELAPSED, kdf.getElapsedTimeMillis());

      secretKey = kdf.deriveKey(newPassphrase.toCharArray(), passphraseSalt);
    }

    byte[] encryptionIV          = generateIV();
    byte[] combinedSecrets       = Util.combine(masterSecret.getEncryptionKey().getEncoded(),
                                                masterSecret.getMacKey().getEncoded());
    byte[] encryptedMasterSecret = encrypt(encryptionIV, combinedSecrets, secretKey);

    Arrays.fill(combinedSecrets, (byte) 0);
    secretKey.destroy();

    prefs.putString(PREF_ENCRYPTION_IV, Base64.encodeWithPadding(encryptionIV));
    prefs.putString(PREF_MASTER_SECRET, Base64.encodeWithPadding(encryptedMasterSecret));
    prefs.putBoolean(PREF_PASSPHRASE_INITIALIZED, true);
    prefs.putBoolean(PREF_KEYSTORE_INITIALIZED, keyStoreAlias != null);
    prefs.putString(PREF_KEYSTORE_ALIAS, keyStoreAlias);

    if (!prefs.commit()) {
      throw new AssertionError("failed to save preferences in MasterSecretUtil");
    }

    // Delete the previous keystore key only after the new one is safely stored.
    if (savedKeyStoreAlias != null && !savedKeyStoreAlias.equals(keyStoreAlias)) {
      KeyStoreHelper.deleteKeyStoreEntry(savedKeyStoreAlias);
    }

    return masterSecret;
  }

  public static MasterSecret changeMasterSecretPassphrase(Context context,
                                                          String originalPassphrase,
                                                          String newPassphrase)
      throws InvalidPassphraseException
  {
    MasterSecret masterSecret = getMasterSecret(context, originalPassphrase);
    changeMasterSecretPassphrase(context, masterSecret, newPassphrase);

    return masterSecret;
  }

  public static MasterSecret getMasterSecret(Context context, String passphrase)
      throws InvalidPassphraseException
  {
    if (!isPassphraseInitialized(context)) {
      throw new InvalidPassphraseException("Passphrase has not been initialized");
    }

    if (!getSharedPreferences(context).contains(PREF_ENCRYPTION_IV)) {
      return getLegacyMasterSecret(context, passphrase);
    }

    SecureSecretKeySpec secretKey;

    byte[]  passphraseSalt        = retrieve(context, PREF_PASSPHRASE_SALT);
    String  serializedParams      = retrieve(context, PREF_KDF_PARAMETERS, "");
    byte[]  encryptedMasterSecret = retrieve(context, PREF_MASTER_SECRET);
    byte[]  encryptionIV          = retrieve(context, PREF_ENCRYPTION_IV);
    boolean hasKeyStoreSecret     = retrieve(context, PREF_KEYSTORE_INITIALIZED, false);
    String  keyStoreAlias         = retrieve(context, PREF_KEYSTORE_ALIAS, KEY_ALIAS_DEFAULT);

    if (isUnencryptedPassphrase(passphrase)) {
      secretKey = getUnencryptedKey();
    } else {
      PassphraseBasedKdf kdf = new PassphraseBasedKdf();

      kdf.setParameters(serializedParams);

      if (hasKeyStoreSecret) {
        try {
          kdf.setHmacKey(KeyStoreHelper.getKeyStoreEntryHmac(keyStoreAlias));
        } catch (UnrecoverableEntryException e) {
          throw new InvalidPassphraseException(e);
        }
      }

      secretKey = kdf.deriveKey(passphrase.toCharArray(), passphraseSalt);
    }

    try {
      byte[] combinedSecrets  = decrypt(encryptionIV, encryptedMasterSecret, secretKey);
      byte[] encryptionSecret = Util.split(combinedSecrets, 16, 20)[0];
      byte[] macSecret        = Util.split(combinedSecrets, 16, 20)[1];

      Arrays.fill(combinedSecrets, (byte) 0);

      MasterSecret masterSecret = new MasterSecret(new SecretKeySpec(encryptionSecret, "AES"),
                                                   new SecretKeySpec(macSecret, "HmacSHA1"));

      Arrays.fill(encryptionSecret, (byte) 0);
      Arrays.fill(macSecret, (byte) 0);

      return masterSecret;
    } catch (BadPaddingException bpe) {
      throw new InvalidPassphraseException(bpe);
    } finally {
      secretKey.destroy();
    }
  }

  /**
   * Reads a master secret written by the pre-Argon2 implementation and migrates it to the new
   * format. This keeps existing installs working across the upgrade.
   */
  private static MasterSecret getLegacyMasterSecret(Context context, String passphrase)
      throws InvalidPassphraseException
  {
    try {
      byte[] encryptedAndMacdMasterSecret = retrieve(context, "master_secret");
      byte[] macSalt                      = retrieve(context, "mac_salt");
      int    iterations                   = retrieve(context, "passphrase_iterations", 100);
      byte[] encryptedMasterSecret        = verifyLegacyMac(macSalt, iterations, encryptedAndMacdMasterSecret, passphrase);
      byte[] encryptionSalt               = retrieve(context, "encryption_salt");
      byte[] combinedSecrets              = decryptLegacyWithPassphrase(encryptionSalt, iterations, encryptedMasterSecret, passphrase);
      byte[] encryptionSecret             = Util.split(combinedSecrets, 16, 20)[0];
      byte[] macSecret                    = Util.split(combinedSecrets, 16, 20)[1];

      Arrays.fill(combinedSecrets, (byte) 0);

      MasterSecret masterSecret = new MasterSecret(new SecretKeySpec(encryptionSecret, "AES"),
                                                   new SecretKeySpec(macSecret, "HmacSHA1"));

      Arrays.fill(encryptionSecret, (byte) 0);
      Arrays.fill(macSecret, (byte) 0);

      // Migrate to the Argon2 format on first successful unlock. For the unencrypted passphrase
      // this is cheap; for a real passphrase this is the only time the legacy format is read.
      changeMasterSecretPassphrase(context, masterSecret, passphrase);

      return masterSecret;
    } catch (GeneralSecurityException | IOException e) {
      throw new InvalidPassphraseException(e);
    }
  }

  public static AsymmetricMasterSecret getAsymmetricMasterSecret(@NonNull  Context context,
                                                                 @Nullable MasterSecret masterSecret)
  {
    try {
      byte[] djbPublicBytes   = retrieve(context, ASYMMETRIC_LOCAL_PUBLIC_DJB);
      byte[] djbPrivateBytes  = retrieve(context, ASYMMETRIC_LOCAL_PRIVATE_DJB);

      ECPublicKey  djbPublicKey  = null;
      ECPrivateKey djbPrivateKey = null;

      if (djbPublicBytes != null) {
        djbPublicKey = new ECPublicKey(djbPublicBytes);
      }

      if (masterSecret != null) {
        MasterCipher masterCipher = new MasterCipher(masterSecret);

        if (djbPrivateBytes != null) {
          djbPrivateKey = masterCipher.decryptKey(djbPrivateBytes);
        }
      }

      return new AsymmetricMasterSecret(djbPublicKey, djbPrivateKey);
    } catch (InvalidKeyException ike) {
      throw new AssertionError(ike);
    }
  }

  public static AsymmetricMasterSecret generateAsymmetricMasterSecret(Context context,
                                                                      MasterSecret masterSecret)
  {
    MasterCipher masterCipher = new MasterCipher(masterSecret);
    ECKeyPair    keyPair      = ECKeyPair.generate();

    save(context, ASYMMETRIC_LOCAL_PUBLIC_DJB, keyPair.getPublicKey().serialize());
    save(context, ASYMMETRIC_LOCAL_PRIVATE_DJB, masterCipher.encryptKey(keyPair.getPrivateKey()));

    return new AsymmetricMasterSecret(keyPair.getPublicKey(), keyPair.getPrivateKey());
  }

  public static MasterSecret generateMasterSecret(Context context, String passphrase) {
    byte[]       encryptionSecret = generateEncryptionSecret();
    byte[]       macSecret        = generateMacSecret();
    MasterSecret masterSecret     = new MasterSecret(new SecretKeySpec(encryptionSecret, "AES"),
                                                     new SecretKeySpec(macSecret, "HmacSHA1"));

    Arrays.fill(encryptionSecret, (byte) 0x00);
    Arrays.fill(macSecret, (byte) 0x00);

    changeMasterSecretPassphrase(context, masterSecret, passphrase);

    return masterSecret;
  }

  /**
   * Whether the app must ask for a passphrase before it can open the database. This is stored in a
   * plain SharedPreferences file because it is needed before the database can be opened.
   */
  public static boolean isPassphraseRequired(@NonNull Context context) {
    SharedPreferences preferences = getSharedPreferences(context);

    if (preferences.contains(PREF_PASSPHRASE_REQUIRED)) {
      return preferences.getBoolean(PREF_PASSPHRASE_REQUIRED, false);
    }

    if (!isPassphraseInitialized(context)) {
      return false;
    }

    boolean required;
    try {
      getMasterSecret(context, UNENCRYPTED_PASSPHRASE);
      required = false;
    } catch (InvalidPassphraseException e) {
      required = true;
    }

    preferences.edit().putBoolean(PREF_PASSPHRASE_REQUIRED, required).commit();
    return required;
  }

  public static boolean hasWrappedDatabaseSecret(@NonNull Context context) {
    return retrieve(context, PREF_DB_SECRET_WRAPPED, (String) null) != null;
  }

  /**
   * Encrypts the raw database secret with the given master secret and stores it in place of the
   * keystore-sealed secret.
   */
  public static void wrapDatabaseSecret(@NonNull Context context,
                                        @NonNull MasterSecret masterSecret,
                                        @NonNull byte[] databaseSecret)
  {
    byte[] wrapped = new MasterCipher(masterSecret).encryptBytes(databaseSecret);

    if (wrapped == null) {
      throw new AssertionError("Failed to wrap the database secret");
    }

    if (!getSharedPreferences(context).edit()
            .putString(PREF_DB_SECRET_WRAPPED, Base64.encodeWithPadding(wrapped))
            .putBoolean(PREF_PASSPHRASE_REQUIRED, true)
            .commit())
    {
      throw new AssertionError("failed to save the wrapped database secret");
    }
  }

  public static @NonNull byte[] unwrapDatabaseSecret(@NonNull Context context,
                                                     @NonNull MasterSecret masterSecret)
      throws InvalidPassphraseException
  {
    String wrapped = retrieve(context, PREF_DB_SECRET_WRAPPED, (String) null);

    if (TextUtils.isEmpty(wrapped)) {
      throw new InvalidPassphraseException("No wrapped database secret");
    }

    try {
      byte[] databaseSecret = new MasterCipher(masterSecret).decryptBytes(Base64.decode(wrapped));

      if (databaseSecret == null || databaseSecret.length != 32) {
        throw new InvalidPassphraseException("Invalid database secret");
      }

      return databaseSecret;
    } catch (IOException | org.signal.libsignal.protocol.InvalidMessageException e) {
      throw new InvalidPassphraseException(e);
    }
  }

  public static void clearDatabaseSecret(@NonNull Context context) {
    getSharedPreferences(context).edit()
        .remove(PREF_DB_SECRET_WRAPPED)
        .putBoolean(PREF_PASSPHRASE_REQUIRED, false)
        .commit();
  }

  public static long getKdfElapsedTimeMillis(Context context) {
    return getSharedPreferences(context).getLong(PREF_KDF_ELAPSED, 0);
  }

  public static boolean isPassphraseInitialized(Context context) {
    SharedPreferences preferences = getSharedPreferences(context);

    return preferences.getBoolean(PREF_PASSPHRASE_INITIALIZED, false) ||
           preferences.contains(PREF_MASTER_SECRET);
  }

  public static boolean hasAsymmericMasterSecret(Context context) {
    return getSharedPreferences(context).contains(ASYMMETRIC_LOCAL_PUBLIC_DJB);
  }

  private static SharedPreferences getSharedPreferences(@NonNull Context context) {
    return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
  }

  private static void save(Context context, String key, int value) {
    if (!getSharedPreferences(context)
                .edit()
                .putInt(key, value)
                .commit())
    {
      throw new AssertionError("failed to save a shared pref in MasterSecretUtil");
    }
  }

  private static void save(Context context, String key, byte[] value) {
    if (!getSharedPreferences(context)
                .edit()
                .putString(key, Base64.encodeWithPadding(value))
                .commit())
    {
      throw new AssertionError("failed to save a shared pref in MasterSecretUtil");
    }
  }

  private static void save(Context context, String key, boolean value) {
    if (!getSharedPreferences(context)
                .edit()
                .putBoolean(key, value)
                .commit())
    {
      throw new AssertionError("failed to save a shared pref in MasterSecretUtil");
    }
  }

  private static byte[] retrieve(Context context, String key) {
    SharedPreferences settings = getSharedPreferences(context);
    String            encoded  = settings.getString(key, "");

    try {
      if (TextUtils.isEmpty(encoded)) return new byte[0];
      else                            return Base64.decode(encoded);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static String retrieve(Context context, String key, String defaultValue) {
    return getSharedPreferences(context).getString(key, defaultValue);
  }

  private static boolean retrieve(Context context, String key, boolean defaultValue) {
    return getSharedPreferences(context).getBoolean(key, defaultValue);
  }

  private static int retrieve(Context context, String key, int defaultValue) {
    return getSharedPreferences(context).getInt(key, defaultValue);
  }

  private static boolean isUnencryptedPassphrase(String passphrase) {
    return UNENCRYPTED_PASSPHRASE.equals(passphrase);
  }

  private static SecureSecretKeySpec getUnencryptedKey() {
    return new SecureSecretKeySpec(new byte[32], "AES");
  }

  private static long getMaxKdfMemory(@NonNull Context context) {
    ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    long            memoryClass     = activityManager != null ? activityManager.getMemoryClass() : 128;
    long            maxMemory       = Math.max(memoryClass * 1024L * 1024L, 32L * 1024L * 1024L);

    return maxMemory / 2;
  }

  private static boolean hasStrongBox(@NonNull Context context) {
    return Build.VERSION.SDK_INT >= 28 &&
           context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE);
  }

  private static byte[] generateEncryptionSecret() {
    try {
      KeyGenerator generator = KeyGenerator.getInstance("AES");
      generator.init(128);

      SecretKey key = generator.generateKey();
      return key.getEncoded();
    } catch (NoSuchAlgorithmException ex) {
      throw new AssertionError(ex);
    }
  }

  private static byte[] generateMacSecret() {
    try {
      KeyGenerator generator = KeyGenerator.getInstance("HmacSHA1");
      return generator.generateKey().getEncoded();
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private static byte[] generateSalt() {
    return Util.getSecretBytes(16);
  }

  private static byte[] generateIV() {
    return Util.getSecretBytes(12);
  }

  private static byte[] encrypt(byte[] iv, byte[] data, SecretKey secretKey) {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
      return cipher.doFinal(data);
    } catch (GeneralSecurityException e) {
      throw new AssertionError(e);
    }
  }

  private static byte[] decrypt(byte[] iv, byte[] data, SecretKey secretKey) throws BadPaddingException {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
      return cipher.doFinal(data);
    } catch (BadPaddingException bpe) {
      throw bpe;
    } catch (GeneralSecurityException e) {
      throw new AssertionError(e);
    }
  }

  // --- Legacy (pre-Argon2) format, read-only ---

  private static SecretKey getLegacyKeyFromPassphrase(String passphrase, byte[] salt, int iterations)
      throws GeneralSecurityException
  {
    PBEKeySpec      keyspec = new PBEKeySpec(passphrase.toCharArray(), salt, iterations);
    SecretKeyFactory skf    = SecretKeyFactory.getInstance("PBEWITHSHA1AND128BITAES-CBC-BC");
    return skf.generateSecret(keyspec);
  }

  private static Cipher getLegacyCipherFromPassphrase(String passphrase, byte[] salt, int iterations, int opMode)
      throws GeneralSecurityException
  {
    SecretKey key    = getLegacyKeyFromPassphrase(passphrase, salt, iterations);
    Cipher    cipher = Cipher.getInstance(key.getAlgorithm());
    cipher.init(opMode, key, new PBEParameterSpec(salt, iterations));

    return cipher;
  }

  private static byte[] decryptLegacyWithPassphrase(byte[] encryptionSalt, int iterations, byte[] data, String passphrase)
      throws GeneralSecurityException, IOException
  {
    Cipher cipher = getLegacyCipherFromPassphrase(passphrase, encryptionSalt, iterations, Cipher.DECRYPT_MODE);
    return cipher.doFinal(data);
  }

  private static byte[] verifyLegacyMac(byte[] macSalt, int iterations, byte[] encryptedAndMacdData, String passphrase)
      throws InvalidPassphraseException, GeneralSecurityException, IOException
  {
    SecretKey     key     = getLegacyKeyFromPassphrase(passphrase, macSalt, iterations);
    byte[]        pbkdf2  = key.getEncoded();
    SecretKeySpec hmacKey = new SecretKeySpec(pbkdf2, "HmacSHA1");
    javax.crypto.Mac hmac = javax.crypto.Mac.getInstance("HmacSHA1");
    hmac.init(hmacKey);

    byte[] encryptedData = new byte[encryptedAndMacdData.length - hmac.getMacLength()];
    System.arraycopy(encryptedAndMacdData, 0, encryptedData, 0, encryptedData.length);

    byte[] givenMac = new byte[hmac.getMacLength()];
    System.arraycopy(encryptedAndMacdData, encryptedAndMacdData.length - hmac.getMacLength(), givenMac, 0, givenMac.length);

    byte[] localMac = hmac.doFinal(encryptedData);

    if (Arrays.equals(givenMac, localMac)) return encryptedData;
    else                                   throw new InvalidPassphraseException("Mac mismatch");
  }
}
