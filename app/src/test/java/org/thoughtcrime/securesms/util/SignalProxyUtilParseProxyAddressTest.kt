package org.thoughtcrime.securesms.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test
import org.signal.network.config.SignalProxy

class SignalProxyUtilParseProxyAddressTest {

  @Test
  fun `parses a socks5 address with an explicit port`() {
    assertThat(SignalProxyUtil.parseProxyAddress("socks5://127.0.0.1:9050"))
      .isEqualTo(SignalProxy("127.0.0.1", 9050, SignalProxy.ProxyKind.SOCKS5))
  }

  @Test
  fun `parses a socks5h address with an explicit port`() {
    assertThat(SignalProxyUtil.parseProxyAddress("socks5h://127.0.0.1:9050"))
      .isEqualTo(SignalProxy("127.0.0.1", 9050, SignalProxy.ProxyKind.SOCKS5))
  }

  @Test
  fun `defaults the socks5 port to 9050`() {
    assertThat(SignalProxyUtil.parseProxyAddress("socks5://127.0.0.1"))
      .isEqualTo(SignalProxy("127.0.0.1", 9050, SignalProxy.ProxyKind.SOCKS5))
  }

  @Test
  fun `parses a signal proxy deep link`() {
    assertThat(SignalProxyUtil.parseProxyAddress("https://signal.tube/#proxy.parker.org"))
      .isEqualTo(SignalProxy("proxy.parker.org", 443, SignalProxy.ProxyKind.SIGNAL))
  }

  @Test
  fun `parses a bare signal proxy host`() {
    assertThat(SignalProxyUtil.parseProxyAddress("proxy.parker.org"))
      .isEqualTo(SignalProxy("proxy.parker.org", 443, SignalProxy.ProxyKind.SIGNAL))
  }

  @Test
  fun `rejects a socks5 address with an invalid port`() {
    assertThat(SignalProxyUtil.parseProxyAddress("socks5://127.0.0.1:99999")).isNull()
  }

  @Test
  fun `rejects a socks5 address with a path`() {
    assertThat(SignalProxyUtil.parseProxyAddress("socks5://127.0.0.1:9050/path")).isNull()
  }

  @Test
  fun `rejects an empty socks5 address`() {
    assertThat(SignalProxyUtil.parseProxyAddress("socks5://")).isNull()
  }

  @Test
  fun `formats a socks5 proxy for display`() {
    val proxy = SignalProxy("127.0.0.1", 9050, SignalProxy.ProxyKind.SOCKS5)
    assertThat(SignalProxyUtil.formatProxyAddress(proxy)).isEqualTo("socks5://127.0.0.1:9050")
  }

  @Test
  fun `formats a signal proxy for display`() {
    val proxy = SignalProxy("proxy.parker.org", 443, SignalProxy.ProxyKind.SIGNAL)
    assertThat(SignalProxyUtil.formatProxyAddress(proxy)).isEqualTo("proxy.parker.org")
  }
}
