package org.thoughtcrime.securesms.crypto;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class PassphraseCryptoTest {

  @Test
  public void secureSecretKeySpec_copiesKeyMaterial() {
    byte[] key = new byte[] { 1, 2, 3, 4 };

    SecureSecretKeySpec spec = new SecureSecretKeySpec(key, "AES");

    key[0] = 9;

    assertArrayEquals(new byte[] { 1, 2, 3, 4 }, spec.getEncoded());
    assertEquals("AES", spec.getAlgorithm());
    assertEquals("RAW", spec.getFormat());
    assertFalse(spec.isDestroyed());
  }

  @Test
  public void secureSecretKeySpec_destroyClearsKeyMaterial() {
    SecureSecretKeySpec spec = new SecureSecretKeySpec(new byte[] { 1, 2, 3, 4 }, "AES");

    spec.destroy();

    assertTrue(spec.isDestroyed());
    try {
      spec.getEncoded();
      throw new AssertionError("Expected getEncoded() to fail after destroy()");
    } catch (IllegalStateException expected) {
      // expected
    }
  }

  @Test
  public void secureSecretKeySpec_blankKeyIsNotReportedAsDestroyed() {
    SecureSecretKeySpec spec = new SecureSecretKeySpec(new byte[32], "AES");

    // A key that was all-zero from the start is "blank", which is separate from being destroyed.
    assertFalse(spec.isDestroyed());
  }

  @Test
  public void secureSecretKeySpec_equality() {
    SecureSecretKeySpec a = new SecureSecretKeySpec(new byte[] { 1, 2, 3, 4 }, "AES");
    SecureSecretKeySpec b = new SecureSecretKeySpec(new byte[] { 1, 2, 3, 4 }, "AES");
    SecureSecretKeySpec c = new SecureSecretKeySpec(new byte[] { 4, 3, 2, 1 }, "AES");

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, c);
  }

  @Test
  public void passwordToBytes_usesPkcs12Encoding() {
    assertArrayEquals(new byte[] { 0x00, 0x61, 0x00, 0x62, 0x00, 0x63, 0x00, 0x00 },
                      PassphraseBasedKdf.passwordToBytes("abc".toCharArray()));
  }

  @Test
  public void passwordToBytes_emptyPassword() {
    assertArrayEquals(new byte[0], PassphraseBasedKdf.passwordToBytes(new char[0]));
    assertArrayEquals(new byte[0], PassphraseBasedKdf.passwordToBytes(null));
  }

  @Test
  public void params_serializeRoundTrip() {
    PassphraseBasedKdf.Params params = new PassphraseBasedKdf.Params(4, 65536, 2);

    PassphraseBasedKdf.Params restored = PassphraseBasedKdf.Params.deserialize(params.serialize());

    assertEquals(4, restored.getIterations());
    assertEquals(65536, restored.getMemory());
    assertEquals(2, restored.getParallelism());
  }
}
