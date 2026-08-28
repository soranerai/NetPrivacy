package dev.soranerai.simhide.policy

import dev.soranerai.simhide.model.AppSimPolicy
import dev.soranerai.simhide.model.BuiltInSimProfiles
import dev.soranerai.simhide.model.SimFilterSet
import dev.soranerai.simhide.model.SimHideConfig
import dev.soranerai.simhide.model.SimNetworkType
import dev.soranerai.simhide.model.SimProfile
import dev.soranerai.simhide.model.SimVisibilityMode
import dev.soranerai.simhide.model.validFavoriteIds
import dev.soranerai.simhide.model.uniqueCountries
import org.json.JSONArray
import org.json.JSONObject

/** Shared wire format for the manager and the two LSPosed host processes. */
object SimPolicyCodec {
    fun encode(config: SimHideConfig): String = JSONObject().apply {
        put("version", 2)
        put("profiles", JSONArray().apply { config.profiles.forEach { put(it.json()) } })
        put("appPolicies", JSONArray().apply { config.appPolicies.forEach { put(it.json()) } })
        put("favoriteProfileIds", JSONArray(config.favoriteProfileIds.validFavoriteIds(config.profiles)))
    }.toString()

    fun decode(raw: String, addBuiltInsIfMissing: Boolean = true): SimHideConfig {
        val root = JSONObject(raw)
        val profiles = root.optJSONArray("profiles").profiles()
        val resolvedProfiles = (if (addBuiltInsIfMissing) {
            BuiltInSimProfiles.all.map { builtin -> profiles.firstOrNull { it.id == builtin.id && it.builtIn } ?: builtin } +
                profiles.filterNot { it.builtIn && BuiltInSimProfiles.all.any { builtin -> builtin.id == it.id } }
        } else profiles).uniqueCountries()
        return SimHideConfig(
            // Built-ins are canonical and may gain fields in an app update (for
            // example the synthetic MSISDNs); preserve only user-created entries.
            profiles = resolvedProfiles,
            appPolicies = root.optJSONArray("appPolicies").policies(),
            favoriteProfileIds = root.optJSONArray("favoriteProfileIds").strings().validFavoriteIds(resolvedProfiles),
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

    private fun JSONArray?.strings(): List<String> = buildList {
        if (this@strings == null) return@buildList
        for (i in 0 until length()) optString(i).takeIf(String::isNotBlank)?.let(::add)
    }

    private fun networkType(value: String) = runCatching { SimNetworkType.valueOf(value) }.getOrDefault(SimNetworkType.LTE)
    private fun mode(value: String) = runCatching { SimVisibilityMode.valueOf(value) }.getOrDefault(SimVisibilityMode.PASSTHROUGH)
}
