package dev.soranerai.netprivacy.hooks

import android.net.DhcpInfo
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import dev.soranerai.netprivacy.NetPrivacyLog
import dev.soranerai.netprivacy.model.WifiProfile
import dev.soranerai.netprivacy.model.validationErrorRes
import dev.soranerai.netprivacy.policy.TargetProcessPolicyBridge
import java.util.concurrent.ConcurrentHashMap
import java.net.InetAddress

/** Client-process Wi-Fi identity hooks for APIs 29–35; unavailable methods are ignored. */
object TargetWifiHooks {
    private val loggedHits = ConcurrentHashMap.newKeySet<String>()

    fun install(classLoader: ClassLoader) {
        findClass("android.net.wifi.WifiInfo", classLoader)?.let(::installWifiInfoHooks)
        findClass("android.net.wifi.WifiManager", classLoader)?.let(::installWifiManagerHooks)
        findClass("android.net.LinkProperties", classLoader)?.let(::installLinkPropertiesHooks)
    }

    private fun installWifiInfoHooks(clazz: Class<*>) {
        hookBefore(clazz, setOf("getSSID")) { it.ssid.quotedSsid() }
        hookBefore(clazz, setOf("getBSSID")) { it.bssid }
        hookBefore(clazz, setOf("getIpAddress")) { it.ipAddress.toDhcpInt() }
        hookBefore(clazz, setOf("getLinkSpeed", "getTxLinkSpeedMbps", "getRxLinkSpeedMbps", "getMaxSupportedTxLinkSpeedMbps", "getMaxSupportedRxLinkSpeedMbps")) { profile -> profile.speed.toPositiveIntOrNull() }
        hookBefore(clazz, setOf("getFrequency")) { it.frequency.toPositiveIntOrNull() }
    }

    private fun installWifiManagerHooks(clazz: Class<*>) {
        val hooks = XposedBridge.hookAllMethods(clazz, "getDhcpInfo", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val profile = wifiProfile() ?: return
                val info = param.result as? DhcpInfo ?: return
                info.ipAddress = profile.ipAddress.toDhcpInt()
                info.gateway = profile.gateway.toDhcpInt()
                info.dns1 = profile.dns1.toDhcpInt()
                info.dns2 = profile.dns2.toDhcpInt()
                info.serverAddress = profile.serverIp.toDhcpInt()
                logHit("WifiManager.getDhcpInfo")
            }
        })
        logInstalled(clazz, "getDhcpInfo", hooks.size)
    }

    private fun installLinkPropertiesHooks(clazz: Class<*>) {
        hookAfter(clazz, "getAddresses") { profile -> listOf(profile.ipAddress.toInetAddress()) }
        hookAfter(clazz, "getDnsServers") { profile -> listOf(profile.dns1.toInetAddress(), profile.dns2.toInetAddress()) }
    }

    private fun hookBefore(clazz: Class<*>, names: Set<String>, value: (WifiProfile) -> Any?) {
        names.forEach { method ->
            val hooks = XposedBridge.hookAllMethods(clazz, method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val profile = wifiProfile() ?: return
                    value(profile)?.let { param.result = it }
                    logHit("${clazz.simpleName}.$method")
                }
            })
            logInstalled(clazz, method, hooks.size)
        }
    }

    private fun hookAfter(clazz: Class<*>, method: String, value: (WifiProfile) -> Any) {
        val hooks = XposedBridge.hookAllMethods(clazz, method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val profile = wifiProfile() ?: return
                if (!param.thisObject.isWifiLinkProperties()) return
                param.result = value(profile)
                logHit("${clazz.simpleName}.$method")
            }
        })
        logInstalled(clazz, method, hooks.size)
    }

    private fun wifiProfile(): WifiProfile? = TargetProcessPolicyBridge.current().let { snapshot ->
        snapshot.wifiPolicyForCurrentProcess()?.let(snapshot::wifiProfileFor)?.takeIf { it.validationErrorRes() == null }
    }

    private fun findClass(name: String, classLoader: ClassLoader): Class<*>? =
        runCatching { Class.forName(name, false, classLoader) }.getOrNull()

    private fun logInstalled(clazz: Class<*>, method: String, count: Int) {
        if (count > 0) NetPrivacyLog.info("hooked target ${clazz.simpleName}.$method ($count)")
    }

    private fun logHit(method: String) {
        if (loggedHits.add(method)) NetPrivacyLog.info("first Wi-Fi policy hit: $method")
    }
}

private fun String.quotedSsid() = if (startsWith('"') && endsWith('"')) this else "\"$this\""
private fun String.toPositiveIntOrNull() = trim().takeWhile(Char::isDigit).toIntOrNull()?.takeIf { it >= 0 }
private fun String.toDhcpInt(): Int {
    val octets = split('.').mapNotNull { it.toIntOrNull()?.takeIf { value -> value in 0..255 } }
    return if (octets.size == 4) octets[0] or (octets[1] shl 8) or (octets[2] shl 16) or (octets[3] shl 24) else 0
}

private fun String.toInetAddress(): InetAddress = InetAddress.getByName(this)
private fun Any.isWifiLinkProperties(): Boolean = runCatching {
    val name = javaClass.getMethod("getInterfaceName").invoke(this) as? String
    name?.startsWith("wlan", ignoreCase = true) == true || name?.startsWith("wifi", ignoreCase = true) == true
}.getOrDefault(false)
