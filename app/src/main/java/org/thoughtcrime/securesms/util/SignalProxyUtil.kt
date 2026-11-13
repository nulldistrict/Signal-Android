package org.thoughtcrime.securesms.util

import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.functions.Predicate
import io.reactivex.rxjava3.schedulers.Schedulers
import org.conscrypt.ConscryptSignal
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.signal.core.util.logging.Log.w
import org.signal.network.config.SignalProxy
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.enableProxy
import org.thoughtcrime.securesms.push.AccountManagerFactory
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import java.io.IOException
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

object SignalProxyUtil {
  private val TAG = tag(SignalProxyUtil::class.java)

  private const val PROXY_LINK_HOST = "signal.tube"
  private const val DEFAULT_SOCKS_PORT = 9050

  private val PROXY_LINK_PATTERN: Pattern = Pattern.compile("^(https|sgnl)://$PROXY_LINK_HOST/#([^:]+).*$")
  private val HOST_PATTERN: Pattern = Pattern.compile("^([^:]+).*$")

  @JvmStatic
  fun startListeningToWebsocket() {
    if (SignalStore.proxy.isProxyEnabled && AppDependencies.authWebSocket.state.firstOrError().blockingGet().isFailure) {
      Log.w(TAG, "Proxy is in a failed state. Restarting.")
      AppDependencies.resetNetwork()
    }

    SignalExecutors.UNBOUNDED.execute { AppDependencies.startNetwork() }
  }

  /**
   * Handles all things related to enabling a proxy, including saving it and resetting the relevant
   * network connections.
   */
  @JvmStatic
  fun enableProxy(proxy: SignalProxy) {
    SignalStore.proxy.enableProxy(proxy)
    ConscryptSignal.setUseEngineSocketByDefault(proxy.kind == SignalProxy.ProxyKind.SIGNAL)
    AppDependencies.resetNetwork()
    startListeningToWebsocket()
  }

  /**
   * Handles all things related to disabling a proxy, including saving the change and resetting the
   * relevant network connections.
   */
  @JvmStatic
  fun disableProxy() {
    SignalStore.proxy.disableProxy()
    ConscryptSignal.setUseEngineSocketByDefault(false)
    AppDependencies.resetNetwork()
    startListeningToWebsocket()
  }

  @JvmStatic
  fun disableAndClearProxy() {
    disableProxy()
    SignalStore.proxy.clearProxy()
  }

  /**
   * A blocking call that will wait until the websocket either successfully connects, or fails.
   * It is assumed that the app state is already configured how you would like it, e.g. you've
   * already configured a proxy if relevant.
   *
   * @return True if the connection is successful within the specified timeout, otherwise false.
   */
  @JvmStatic
  @WorkerThread
  fun testWebsocketConnection(timeout: Long): Boolean {
    startListeningToWebsocket()

    if (!SignalStore.account.isRegistered || SignalStore.account.aci == null) {
      Log.i(TAG, "User is unregistered! Doing simple check.")
      return testWebsocketConnectionUnregistered(timeout)
    }

    return AppDependencies.authWebSocket
      .state
      .subscribeOn(Schedulers.trampoline())
      .observeOn(Schedulers.trampoline())
      .timeout(timeout, TimeUnit.MILLISECONDS)
      .skipWhile(Predicate { state: WebSocketConnectionState -> state != WebSocketConnectionState.CONNECTED && !state.isFailure })
      .firstOrError()
      .flatMap<Boolean>(Function { state: WebSocketConnectionState? -> Single.just(state == WebSocketConnectionState.CONNECTED) })
      .onErrorReturn(Function { _: Throwable? -> false })
      .blockingGet()
  }

  /**
   * If this is a valid proxy deep link, this will return the embedded host. If not, it will return
   * null.
   */
  @JvmStatic
  fun parseHostFromProxyDeepLink(proxyLink: String?): String? {
    if (proxyLink == null) {
      return null
    }

    val matcher = PROXY_LINK_PATTERN.matcher(proxyLink)

    return when {
      matcher.matches() -> matcher.group(2)
      else -> null
    }
  }

  /**
   * Takes in an address that could be in various formats, and converts it to the format we should
   * be storing and connecting to.
   */
  @JvmStatic
  fun convertUserEnteredAddressToHost(host: String): String {
    val parsedHost = parseHostFromProxyDeepLink(host)
    if (parsedHost != null) {
      return parsedHost
    }

    val matcher = HOST_PATTERN.matcher(host)

    return when {
      matcher.matches() -> matcher.group(1) ?: ""
      else -> host
    }
  }

  /**
   * Parses a user-entered proxy address.
   *
   * Supported formats:
   * - Signal proxy link or bare hostname: `https://signal.tube/#proxy.example.org`, `proxy.example.org`
   * - SOCKS5: `socks5://127.0.0.1:9050` or `socks5h://127.0.0.1:9050` (port defaults to 9050)
   *
   * SOCKS5 always uses remote DNS resolution (`socks5h`) so hostnames are never resolved locally.
   *
   * @return the parsed proxy, or null when the address is invalid.
   */
  @JvmStatic
  fun parseProxyAddress(input: String): SignalProxy? {
    val text = input.trim()
    if (text.isEmpty()) {
      return null
    }

    val proxyHost = parseHostFromProxyDeepLink(text)
    if (proxyHost != null) {
      return if (proxyHost.isEmpty()) null else SignalProxy(proxyHost, 443, SignalProxy.ProxyKind.SIGNAL)
    }

    val lower = text.lowercase()
    if (lower.startsWith("socks5://") || lower.startsWith("socks5h://")) {
      return try {
        val uri = URI(text)
        val host = uri.host
        val port = if (uri.port == -1) DEFAULT_SOCKS_PORT else uri.port

        if (host.isNullOrEmpty() || port !in 1..65535 || !uri.rawPath.isNullOrEmpty() || uri.rawQuery != null) {
          null
        } else {
          SignalProxy(host, port, SignalProxy.ProxyKind.SOCKS5)
        }
      } catch (e: Exception) {
        Log.w(TAG, "Invalid SOCKS5 proxy address.", e)
        null
      }
    }

    val host = convertUserEnteredAddressToHost(text)
    if (host.isEmpty() || host.contains(' ') || host.contains('/')) {
      return null
    }

    return SignalProxy(host, 443, SignalProxy.ProxyKind.SIGNAL)
  }

  /** Formats a stored proxy for display in the address field. */
  @JvmStatic
  fun formatProxyAddress(proxy: SignalProxy?): String {
    return when (proxy?.kind) {
      null -> ""
      SignalProxy.ProxyKind.SIGNAL -> proxy.host
      SignalProxy.ProxyKind.SOCKS5 -> "socks5://${proxy.host}:${proxy.port}"
    }
  }

  @JvmStatic
  fun generateProxyUrl(link: String): String {
    var host: String = link
    val parsed = parseHostFromProxyDeepLink(link)

    if (parsed != null) {
      host = parsed
    }

    val matcher = HOST_PATTERN.matcher(host)

    if (matcher.matches()) {
      host = matcher.group(1)!!
    }

    return "https://$PROXY_LINK_HOST/#$host"
  }

  private fun testWebsocketConnectionUnregistered(timeout: Long): Boolean {
    val latch = CountDownLatch(1)
    val success = AtomicBoolean(false)
    val accountManager = AccountManagerFactory.getInstance()
      .createUnauthenticated(AppDependencies.application, "", SignalServiceAddress.DEFAULT_DEVICE_ID, "")

    SignalExecutors.UNBOUNDED.execute {
      try {
        accountManager.checkNetworkConnection()
        success.set(true)
        latch.countDown()
      } catch (_: IOException) {
        latch.countDown()
      }
    }

    try {
      latch.await(timeout, TimeUnit.MILLISECONDS)
    } catch (e: InterruptedException) {
      w(TAG, "Interrupted!", e)
    }

    return success.get()
  }
}
