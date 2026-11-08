package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.Util;
import org.signal.network.config.SignalProxy;

import java.util.Arrays;
import java.util.List;

public final class ProxyValues extends SignalStoreValues {

  private static final String  KEY_PROXY_ENABLED = "proxy.enabled";
  private static final String  KEY_HOST          = "proxy.host";
  private static final String  KEY_PORT          = "proxy.port";
  private static final String  KEY_KIND          = "proxy.kind";

  ProxyValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Arrays.asList(KEY_PROXY_ENABLED, KEY_HOST, KEY_PORT, KEY_KIND);
  }

  public void enableProxy(@NonNull SignalProxy proxy) {
    if (Util.isEmpty(proxy.getHost())) {
      throw new IllegalArgumentException("Empty host!");
    }

    getStore().beginWrite()
              .putBoolean(KEY_PROXY_ENABLED, true)
              .putString(KEY_HOST, proxy.getHost())
              .putInteger(KEY_PORT, proxy.getPort())
              .putString(KEY_KIND, proxy.getKind().name())
              .apply();
  }

  /**
   * Disables the proxy, but does not clear out the last-chosen host.
   */
  public void disableProxy() {
    putBoolean(KEY_PROXY_ENABLED, false);
  }

  public boolean isProxyEnabled() {
    return getBoolean(KEY_PROXY_ENABLED, false);
  }

  /**
   * Sets the proxy. This does not *enable* the proxy. This is because the user may want to set a
   * proxy and then enabled it and disable it at will.
   */
  public void setProxy(@Nullable SignalProxy proxy) {
    if (proxy != null) {
      getStore().beginWrite()
                .putString(KEY_HOST, proxy.getHost())
                .putInteger(KEY_PORT, proxy.getPort())
                .putString(KEY_KIND, proxy.getKind().name())
                .apply();
    } else {
      getStore().beginWrite()
                .remove(KEY_HOST)
                .remove(KEY_PORT)
                .remove(KEY_KIND)
                .apply();
    }
  }

  public @Nullable SignalProxy getProxy() {
    String host = getString(KEY_HOST, null);
    int    port = getInteger(KEY_PORT, 0);

    if (host != null) {
      return new SignalProxy(host, port, parseKind(getString(KEY_KIND, null)));
    } else {
      return null;
    }
  }

  private static @NonNull SignalProxy.ProxyKind parseKind(@Nullable String value) {
    if (value != null) {
      for (SignalProxy.ProxyKind kind : SignalProxy.ProxyKind.values()) {
        if (kind.name().equals(value)) {
          return kind;
        }
      }
    }

    // Existing installs only stored Signal TLS proxies, so default to that.
    return SignalProxy.ProxyKind.SIGNAL;
  }

  public @Nullable String getProxyHost() {
    SignalProxy proxy = getProxy();
    return proxy !=  null ? proxy.getHost() : null;
  }
}
