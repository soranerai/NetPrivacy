package dev.soranerai.netprivacy.policy

import dev.soranerai.netprivacy.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Shared JSON wire format for all currently supported configuration domains. */
object ConfigCodec {
    fun encode(config: HideConfig): String = JSONObject().apply {
        put("version", 3)
        put("profiles", JSONArray().apply { config.simProfiles.forEach { put(it.json()) } })
        put("appPolicies", JSONArray().apply { config.simPolicies.forEach { put(it.json()) } })
        put("favoriteProfileIds", JSONArray(config.favoriteSimProfileIds.validFavoriteIds(config.simProfiles)))
        put("wifiProfiles", JSONArray().apply { config.wifiProfiles.forEach { put(it.json()) } })
        put("appWifiPolicies", JSONArray().apply { config.wifiPolicies.forEach { put(it.json()) } })
    }.toString()

    fun decode(raw: String, addBuiltInsIfMissing: Boolean = true): HideConfig {
        val root = JSONObject(raw)
        val loadedSimProfiles = root.optJSONArray("profiles").simProfiles()
        val simProfiles = (if (addBuiltInsIfMissing) resolveSimProfiles(loadedSimProfiles) else loadedSimProfiles).uniqueCountries()
        val loadedWifiProfiles = root.optJSONArray("wifiProfiles").wifiProfiles()
        return HideConfig(
            simProfiles = simProfiles,
            simPolicies = root.optJSONArray("appPolicies").simPolicies(),
            favoriteSimProfileIds = root.optJSONArray("favoriteProfileIds").strings().validFavoriteIds(simProfiles),
            wifiProfiles = if (addBuiltInsIfMissing) resolveWifiProfiles(loadedWifiProfiles) else loadedWifiProfiles,
            wifiPolicies = root.optJSONArray("appWifiPolicies").wifiPolicies(),
        )
    }

    private fun resolveSimProfiles(profiles: List<SimProfile>) =
        BuiltInSimProfiles.all.map { builtin -> profiles.firstOrNull { it.id == builtin.id && it.builtIn } ?: builtin } +
            profiles.filterNot { it.builtIn && BuiltInSimProfiles.all.any { builtin -> builtin.id == it.id } }

    private fun resolveWifiProfiles(profiles: List<WifiProfile>) =
        BuiltInWifiProfiles.all.map { builtin -> profiles.firstOrNull { it.id == builtin.id && it.builtIn } ?: builtin } +
            profiles.filterNot { it.builtIn && BuiltInWifiProfiles.all.any { builtin -> builtin.id == it.id } }

    private fun SimProfile.json() = JSONObject().apply {
        put("id", id); put("name", name); put("countryIso", countryIso); put("mcc", mcc); put("mnc", mnc); put("operatorName", operatorName)
        put("networkType", networkType.name); put("roaming", roaming); put("builtIn", builtIn); put("phoneNumber", phoneNumber)
    }
    private fun AppSimPolicy.json() = JSONObject().apply {
        put("packageName", packageName); put("userId", userId); put("uid", uid); put("mode", mode.name); put("profileId", profileId)
        put("filters", JSONObject().apply { put("operator", filters.operator); put("subscription", filters.subscription); put("cellInfo", filters.cellInfo); put("identifiers", filters.identifiers) })
    }
    private fun WifiProfile.json() = JSONObject().apply {
        put("id", id); put("name", name); put("ssid", ssid); put("bssid", bssid); put("ipAddress", ipAddress); put("speed", speed); put("frequency", frequency)
        put("gateway", gateway); put("dns1", dns1); put("dns2", dns2); put("serverIp", serverIp); put("builtIn", builtIn)
    }
    private fun AppWifiPolicy.json() = JSONObject().apply { put("packageName", packageName); put("userId", userId); put("uid", uid); put("profileId", profileId) }

    private fun JSONArray?.simProfiles(): List<SimProfile> = buildList {
        if (this@simProfiles == null) return@buildList
        for (i in 0 until length()) optJSONObject(i)?.let { item ->
            item.optString("id").takeIf(String::isNotBlank)?.let { id -> add(SimProfile(id, item.optString("name"), item.optString("countryIso"), item.optString("mcc"), item.optString("mnc"), item.optString("operatorName"), networkType(item.optString("networkType")), item.optBoolean("roaming"), item.optBoolean("builtIn"), item.optString("phoneNumber"))) }
        }
    }
    private fun JSONArray?.simPolicies(): List<AppSimPolicy> = buildList {
        if (this@simPolicies == null) return@buildList
        for (i in 0 until length()) optJSONObject(i)?.let { item ->
            val packageName = item.optString("packageName"); if (packageName.isBlank()) return@let
            val filters = item.optJSONObject("filters"); add(AppSimPolicy(packageName, item.optInt("userId"), item.optInt("uid"), mode(item.optString("mode")), item.optString("profileId").ifBlank { null }, SimFilterSet(filters?.optBoolean("operator", true) ?: true, filters?.optBoolean("subscription", true) ?: true, filters?.optBoolean("cellInfo", true) ?: true, filters?.optBoolean("identifiers", true) ?: true)))
        }
    }
    private fun JSONArray?.wifiProfiles(): List<WifiProfile> = buildList {
        if (this@wifiProfiles == null) return@buildList
        for (i in 0 until length()) optJSONObject(i)?.let { item -> item.optString("id").takeIf(String::isNotBlank)?.let { id -> add(WifiProfile(id, item.optString("name"), item.optString("ssid"), item.optString("bssid"), item.optString("ipAddress"), item.optString("speed"), item.optString("frequency"), item.optString("gateway"), item.optString("dns1"), item.optString("dns2"), item.optString("serverIp"), item.optBoolean("builtIn"))) } }
    }
    private fun JSONArray?.wifiPolicies(): List<AppWifiPolicy> = buildList {
        if (this@wifiPolicies == null) return@buildList
        for (i in 0 until length()) optJSONObject(i)?.let { item -> val packageName = item.optString("packageName"); val profileId = item.optString("profileId"); if (packageName.isNotBlank() && profileId.isNotBlank()) add(AppWifiPolicy(packageName, item.optInt("userId"), item.optInt("uid"), profileId)) }
    }
    private fun JSONArray?.strings(): List<String> = buildList { if (this@strings != null) for (i in 0 until length()) optString(i).takeIf(String::isNotBlank)?.let(::add) }
    private fun networkType(value: String) = runCatching { SimNetworkType.valueOf(value) }.getOrDefault(SimNetworkType.LTE)
    private fun mode(value: String) = runCatching { SimVisibilityMode.valueOf(value) }.getOrDefault(SimVisibilityMode.PASSTHROUGH)
}
