package dev.soranerai.netprivacy.model

import dev.soranerai.netprivacy.R

/** Synthetic Wi-Fi and DHCP values reserved for future hooks. */
data class WifiProfile(
    val id: String,
    val name: String,
    val ssid: String,
    val bssid: String,
    val ipAddress: String,
    val speed: String = "",
    val frequency: String = "",
    val gateway: String,
    val dns1: String,
    val dns2: String,
    val serverIp: String,
    val builtIn: Boolean = false,
)

data class AppWifiPolicy(
    val packageName: String,
    val userId: Int,
    val uid: Int,
    val profileId: String,
)

object BuiltInWifiProfiles {
    val all = listOf(
        WifiProfile(
            id = "builtin-wifi-home", name = "Home Wi-Fi", ssid = "Home Wi-Fi",
            bssid = "02:00:00:00:00:01", ipAddress = "192.168.1.100", speed = "433 Mbps",
            frequency = "5180 MHz", gateway = "192.168.1.1", dns1 = "1.1.1.1",
            dns2 = "8.8.8.8", serverIp = "192.168.1.1", builtIn = true,
        ),
    )
}

fun WifiProfile.validationErrorRes(): Int? = when {
    name.isBlank() -> R.string.validation_wifi_name
    ssid.isBlank() -> R.string.validation_ssid
    bssid.isBlank() -> R.string.validation_bssid
    !ipAddress.isIpv4() -> R.string.validation_ip_address
    !gateway.isIpv4() -> R.string.validation_gateway
    !dns1.isIpv4() -> R.string.validation_dns1
    !dns2.isIpv4() -> R.string.validation_dns2
    !serverIp.isIpv4() -> R.string.validation_server_ip
    else -> null
}

private fun String.isIpv4(): Boolean {
    val octets = split('.')
    return octets.size == 4 && octets.all { it.toIntOrNull()?.let { value -> value in 0..255 } == true }
}
