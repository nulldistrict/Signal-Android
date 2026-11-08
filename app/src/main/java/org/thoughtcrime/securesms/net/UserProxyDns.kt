/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.net

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * DNS for OkHttp traffic.
 *
 * Normally this delegates to Signal's regular resolver. When the user has configured a SOCKS5
 * proxy, hostnames are instead resolved with DNS-over-HTTPS through that proxy. This prevents a
 * DNS leak: neither the local network nor the OS resolver learns which hostnames the app is
 * connecting to.
 */
object UserProxyDns : Dns {

  private val TAG = Log.tag(UserProxyDns::class.java)

  private val dohClients: List<DnsOverHttps> by lazy {
    listOf(
      buildDohClient("https://1.1.1.1/dns-query"),
      buildDohClient("https://9.9.9.9/dns-query")
    )
  }

  override fun lookup(hostname: String): List<InetAddress> {
    if (UserProxySelector.getUserSocksProxy() == null) {
      return SignalServiceNetworkAccess.DNS.lookup(hostname)
    }

    for (client in dohClients) {
      try {
        val addresses = client.lookup(hostname)
        if (addresses.isNotEmpty()) {
          return addresses
        }
      } catch (e: UnknownHostException) {
        Log.w(TAG, "DoH lookup failed for $hostname, trying the next resolver.")
      } catch (e: Exception) {
        Log.w(TAG, "DoH lookup failed for $hostname, trying the next resolver.", e)
      }
    }

    // Fail closed: do not fall back to the system resolver while a SOCKS proxy is active.
    throw UnknownHostException(hostname)
  }

  private fun buildDohClient(url: String): DnsOverHttps {
    val client = OkHttpClient.Builder()
      .proxySelector(UserProxySelector)
      .dns(Dns.SYSTEM)
      .build()

    return DnsOverHttps.Builder()
      .url(url.toHttpUrl())
      .client(client)
      .includeIPv6(false)
      .build()
  }
}
