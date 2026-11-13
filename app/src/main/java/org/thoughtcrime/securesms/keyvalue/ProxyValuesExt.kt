/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import org.signal.network.config.SignalProxy
import org.signal.storage.keyvalue.ProxyValues

// Fork: the proxy kind (Signal TLS proxy or user SOCKS5 proxy) is stored next to upstream's host and port. It
// is not part of the backup keys; a restored SOCKS5 proxy comes back as a Signal proxy and fails closed until
// the address is entered again.
private const val KEY_KIND = "proxy.kind"

/** The last-chosen proxy, whether or not it's currently enabled. */
val ProxyValues.proxy: SignalProxy?
  get() = proxyHost?.let { SignalProxy(it, proxyPort, parseProxyKind(store.getString(KEY_KIND, null))) }

fun ProxyValues.enableProxy(proxy: SignalProxy) {
  enableProxy(proxy.host, proxy.port)
  store.beginWrite().putString(KEY_KIND, proxy.kind.name).apply()
}

private fun parseProxyKind(value: String?): SignalProxy.ProxyKind {
  // Installs from before SOCKS5 support only stored Signal TLS proxies.
  return SignalProxy.ProxyKind.entries.firstOrNull { it.name == value } ?: SignalProxy.ProxyKind.SIGNAL
}
