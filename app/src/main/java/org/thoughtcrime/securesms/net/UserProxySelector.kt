/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.net

import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.proxy
import org.signal.network.config.SignalProxy
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Applies a user-configured SOCKS5 proxy to OkHttp traffic.
 *
 * When no user SOCKS5 proxy is configured this delegates to the system proxy selector, preserving
 * Signal's existing behavior. When one is configured, it is used for every request so that traffic
 * cannot silently bypass the proxy.
 *
 * Note that OkHttp resolves names through [UserProxyDns], which performs DNS-over-HTTPS through the
 * same proxy, so lookups do not leak to the local network.
 */
object UserProxySelector : ProxySelector() {

  private val systemDefault: ProxySelector? = getDefault()

  /**
   * A SOCKS proxy pointing at the discard service on localhost. Used when a proxy is configured but
   * its address is invalid, so that traffic fails closed instead of leaking.
   */
  private val FAIL_CLOSED_PROXY: Proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", 9))

  /** @return the user SOCKS5 proxy if one is enabled and valid, otherwise null. */
  @JvmStatic
  fun getUserSocksProxy(): Proxy? {
    if (!SignalStore.proxy.isProxyEnabled) {
      return null
    }

    val configured = SignalStore.proxy.proxy ?: return null
    if (configured.kind != SignalProxy.ProxyKind.SOCKS5) {
      return null
    }

    return try {
      if (configured.host.isBlank() || configured.port !in 1..65535) {
        FAIL_CLOSED_PROXY
      } else {
        Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(configured.host, configured.port))
      }
    } catch (e: IllegalArgumentException) {
      FAIL_CLOSED_PROXY
    }
  }

  override fun select(uri: URI?): List<Proxy> {
    val socks = getUserSocksProxy()
    if (socks != null) {
      return listOf(socks)
    }

    return systemDefault?.select(uri) ?: listOf(Proxy.NO_PROXY)
  }

  override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
    systemDefault?.connectFailed(uri, sa, ioe)
  }
}
