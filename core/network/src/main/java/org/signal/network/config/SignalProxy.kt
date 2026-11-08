/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.config

class SignalProxy @JvmOverloads constructor(
  val host: String,
  val port: Int,
  val kind: ProxyKind = ProxyKind.SIGNAL
) {

  fun toProxyConfig(): ProxyConfig {
    return when (kind) {
      ProxyKind.SIGNAL -> ProxyConfig.ProxyAddress(ProxyConfig.ProxyScheme.TLS, host, port)
      ProxyKind.SOCKS5 -> ProxyConfig.ProxyAddress(ProxyConfig.ProxyScheme.SOCKS_REMOTE_DNS, host, port)
    }
  }

  enum class ProxyKind {
    /** Signal's TLS proxy, identified by a `signal.tube` link. */
    SIGNAL,

    /** A user-provided SOCKS5 proxy (for example Orbot). Always uses remote DNS (`socks5h`). */
    SOCKS5
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SignalProxy) return false
    return host == other.host && port == other.port && kind == other.kind
  }

  override fun hashCode(): Int {
    var result = host.hashCode()
    result = 31 * result + port
    result = 31 * result + kind.hashCode()
    return result
  }
}
