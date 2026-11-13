package top.orangetv.util

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 取本机可被手机访问的局域网 IPv4。
 *
 * 电视盒子常同时有 wlan0 与 eth0，甚至还有 p2p / 虚拟网卡，
 * 所以按「名字像物理网卡的优先，其次私有地址」排序，避免给出 169.254.x.x 这种自分配地址。
 */
object LanAddress {

    fun pick(): String? {
        val candidates = mutableListOf<Pair<Int, String>>()

        val interfaces = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        }.getOrElse { emptyList() }

        for (nic in interfaces) {
            val up = runCatching { nic.isUp && !nic.isLoopback }.getOrElse { false }
            if (!up) continue

            val name = nic.name.orEmpty()
            val rank = when {
                name.startsWith("wlan") -> 0
                name.startsWith("eth") -> 1
                name.startsWith("ap") -> 2
                name.startsWith("rmnet") || name.startsWith("p2p") -> 9
                else -> 5
            }

            val addresses = runCatching { nic.inetAddresses.toList() }.getOrElse { emptyList() }
            for (addr in addresses) {
                if (addr !is Inet4Address) continue
                if (addr.isLoopbackAddress) continue
                if (!addr.isSiteLocalAddress) continue
                val host = addr.hostAddress ?: continue
                candidates += rank to host
            }
        }

        return candidates.minByOrNull { it.first }?.second
    }
}
