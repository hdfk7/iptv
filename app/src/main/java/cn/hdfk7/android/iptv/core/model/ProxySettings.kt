package cn.hdfk7.android.iptv.core.model

data class ProxySettings(
    val enabled: Boolean = false,
    val host: String = "",
    val port: String = "",
    val username: String = "",
    val password: String = ""
) {
    fun valid(): Boolean {
        if (!enabled) {
            return true
        }
        val ipv4 = host.split('.').let(block = { parts -> parts.size == 4 && parts.all(predicate = { part -> part.isNotEmpty() && part.all(predicate = Char::isDigit) && (part.toIntOrNull() ?: -1) in 0..255 }) })
        val ipv6 = host.contains(':') && host.all(predicate = { char -> char in "0123456789abcdefABCDEF:" }) && runCatching(block = { java.net.InetAddress.getByName(host) is java.net.Inet6Address }).getOrDefault(false)
        return (ipv4 || ipv6) && (port.toIntOrNull() ?: 0) in 1..65535 && !username.contains(':') && !username.contains('\n') && !password.contains('\n') && (username.isNotEmpty() || password.isEmpty())
    }

    override fun toString(): String = "ProxySettings(enabled=$enabled)"
}
