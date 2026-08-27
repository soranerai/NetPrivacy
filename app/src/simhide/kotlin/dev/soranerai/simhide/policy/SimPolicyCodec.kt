package dev.soranerai.simhide.policy

import dev.soranerai.simhide.model.AppSimPolicy
import dev.soranerai.simhide.model.BuiltInSimProfiles
import dev.soranerai.simhide.model.SimFilterSet
import dev.soranerai.simhide.model.SimHideConfig
import dev.soranerai.simhide.model.SimNetworkType
import dev.soranerai.simhide.model.SimProfile
import dev.soranerai.simhide.model.SimVisibilityMode
import org.json.JSONArray
import org.json.JSONObject

/** Shared wire format for the manager and the two LSPosed host processes. */
object SimPolicyCodec {
    fun encode(config: SimHideConfig): String = JSONObject().apply {
        put("version", 1)
        put("profiles", JSONArray().apply { config.profiles.forEach { put(it.json()) } })
        put("appPolicies", JSONArray().apply { config.appPolicies.forEach { put(it.json()) } })
    }.toString()

    fun decode(raw: String, addBuiltInsIfMissing: Boolean = true): SimHideConfig {
        val root = JSONObject(raw)
        val profiles = root.optJSONArray("profiles").profiles()
        return SimHideConfig(
            profiles = if (addBuiltInsIfMissing && profiles.none { it.builtIn }) BuiltInSimProfiles.all + profiles else profiles,
            appPolicies = root.optJSONArray("appPolicies").policies(),
        )
    }

    private fun SimProfile.json() = JSONObject().apply {
        put("id", id); put("name", name); put("countryIso", countryIso); put("mcc", mcc); put("mnc", mnc)
        put("operatorName", operatorName); put("networkType", networkType.name); put("roaming", roaming); put("builtIn", builtIn); put("phoneNumber", phoneNumber)
    }

    private fun AppSimPolicy.json() = JSONObject().apply {
        put("packageName", packageName); put("userId", userId); put("uid", uid); put("mode", mode.name); put("profileId", profileId)
        put("filters", JSONObject().apply { put("operator", filters.operator); put("subscription", filters.subscription); put("cellInfo", filters.cellInfo); put("identifiers", filters.identifiers) })
    }

    private fun JSONArray?.profiles(): List<SimProfile> = buildList {
        if (this@profiles == null) return@buildList
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            val id = item.optString("id"); if (id.isBlank()) continue
            add(SimProfile(id, item.optString("name"), item.optString("countryIso"), item.optString("mcc"), item.optString("mnc"), item.optString("operatorName"), networkType(item.optString("networkType")), item.optBoolean("roaming"), item.optBoolean("builtIn"), item.optString("phoneNumber")))
        }
    }

    private fun JSONArray?.policies(): List<AppSimPolicy> = buildList {
        if (this@policies == null) return@buildList
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            val packageName = item.optString("packageName"); if (packageName.isBlank()) continue
            val filters = item.optJSONObject("filters")
            add(AppSimPolicy(packageName, item.optInt("userId"), item.optInt("uid"), mode(item.optString("mode")), item.optString("profileId").ifBlank { null }, SimFilterSet(filters?.optBoolean("operator", true) ?: true, filters?.optBoolean("subscription", true) ?: true, filters?.optBoolean("cellInfo", true) ?: true, filters?.optBoolean("identifiers", true) ?: true)))
        }
    }

    private fun networkType(value: String) = runCatching { SimNetworkType.valueOf(value) }.getOrDefault(SimNetworkType.LTE)
    private fun mode(value: String) = runCatching { SimVisibilityMode.valueOf(value) }.getOrDefault(SimVisibilityMode.PASSTHROUGH)
}
