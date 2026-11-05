package cn.hdfk7.android.iptv.core.data.network

import cn.hdfk7.android.iptv.core.model.ProxySettings
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object ProxyHttpClient {
    fun create(settings: ProxySettings, connectMs: Int = 15000, readMs: Int = 15000): OkHttpClient {
        require(settings.valid())
        val builder = OkHttpClient.Builder().connectTimeout(connectMs.toLong(), TimeUnit.MILLISECONDS).readTimeout(readMs.toLong(), TimeUnit.MILLISECONDS)
        builder.proxy(if (settings.enabled) Proxy(Proxy.Type.HTTP, InetSocketAddress(settings.host, settings.port.toInt())) else Proxy.NO_PROXY)
        if (settings.enabled && settings.username.isNotEmpty()) {
            builder.proxyAuthenticator(proxyAuthenticator = { _, response ->
                if (response.request.header("Proxy-Authorization") != null) {
                    null
                } else {
                    response.request.newBuilder().header("Proxy-Authorization", Credentials.basic(settings.username, settings.password)).build()
                }
            })
        }
        return builder.build()
    }
}
