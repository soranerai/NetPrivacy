package dev.soranerai.simhide.data

import android.content.Context
import dev.soranerai.simhide.model.AppSimPolicy
import dev.soranerai.simhide.model.BuiltInSimProfiles
import dev.soranerai.simhide.model.SimFilterSet
import dev.soranerai.simhide.model.SimHideConfig
import dev.soranerai.simhide.model.SimNetworkType
import dev.soranerai.simhide.model.SimProfile
import dev.soranerai.simhide.model.SimVisibilityMode
import dev.soranerai.simhide.model.validFavoriteIds
import dev.soranerai.simhide.model.uniqueCountries
import dev.soranerai.simhide.policy.SimPolicyCodec
import dev.soranerai.simhide.policy.TargetPolicyGrants
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Small, dependency-free, atomic policy store shared by the manager and LSPosed
 * processes. A completed rename is the publication boundary for a policy change.
 */
class SimConfigStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val file = File(applicationContext.createDeviceProtectedStorageContext().filesDir, FILE_NAME)
    private val tempFile = File(file.parentFile, "$FILE_NAME.tmp")
    private val grants = TargetPolicyGrants(applicationContext)
    private val lock = Any()

    fun read(): SimHideConfig = synchronized(lock) {
        if (!file.exists()) return defaultConfig()
        runCatching { parse(file.readText()) }.getOrElse { defaultConfig() }
    }

    /** Device-protected local storage is the sole policy source; the provider brokers reads. */
    fun write(config: SimHideConfig): Result<Unit> = synchronized(lock) {
        val previous = read()
        file.parentFile?.mkdirs()
        tempFile.writeText(SimPolicyCodec.encode(config))
        if (!tempFile.renameTo(file)) {
            return@synchronized Result.failure(IllegalStateException("Не удалось атомарно сохранить конфигурацию"))
        }
        grants.sync(previous.appPolicies, config.appPolicies)
    }

    fun restoreTargetGrants(): Result<Unit> = grants.sync(emptyList(), read().appPolicies)

    private fun defaultConfig() = SimHideConfig(profiles = BuiltInSimProfiles.all)

    private fun parse(raw: String): SimHideConfig {
        val root = JSONObject(raw)
        val profiles = root.optJSONArray("profiles").toProfiles()
        val policies = root.optJSONArray("appPolicies").toPolicies()
        val resolvedProfiles = (BuiltInSimProfiles.all.map { builtin -> profiles.firstOrNull { it.id == builtin.id && it.builtIn } ?: builtin } +
            profiles.filterNot { it.builtIn && BuiltInSimProfiles.all.any { builtin -> builtin.id == it.id } }).uniqueCountries()
        return SimHideConfig(
            profiles = resolvedProfiles,
            appPolicies = policies,
            favoriteProfileIds = root.optJSONArray("favoriteProfileIds").toStrings().validFavoriteIds(resolvedProfiles),
        )
    }
}

private const val FILE_NAME = "simhide_policy.json"

private fun SimHideConfig.toJson() = JSONObject().apply {
    put("version", 2)
    put("profiles", JSONArray().apply { profiles.forEach { put(it.toJson()) } })
    put("appPolicies", JSONArray().apply { appPolicies.forEach { put(it.toJson()) } })
    put("favoriteProfileIds", JSONArray(favoriteProfileIds.validFavoriteIds(profiles)))
}

private fun SimProfile.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("countryIso", countryIso); put("mcc", mcc); put("mnc", mnc)
    put("operatorName", operatorName); put("networkType", networkType.name); put("roaming", roaming); put("builtIn", builtIn); put("phoneNumber", phoneNumber)
}

private fun AppSimPolicy.toJson() = JSONObject().apply {
    put("packageName", packageName); put("userId", userId); put("uid", uid); put("mode", mode.name); put("profileId", profileId)
    put("filters", JSONObject().apply {
        put("operator", filters.operator); put("subscription", filters.subscription)
        put("cellInfo", filters.cellInfo); put("identifiers", filters.identifiers)
    })
}

private fun JSONArray?.toProfiles(): List<SimProfile> = buildList {
    if (this@toProfiles == null) return@buildList
    for (index in 0 until length()) {
        val item = optJSONObject(index) ?: continue
        val id = item.optString("id")
        if (id.isBlank()) continue
        add(SimProfile(
            id = id, name = item.optString("name"), countryIso = item.optString("countryIso"),
            mcc = item.optString("mcc"), mnc = item.optString("mnc"), operatorName = item.optString("operatorName"),
            networkType = item.optString("networkType").toNetworkType(), roaming = item.optBoolean("roaming"),
            builtIn = item.optBoolean("builtIn"),
            phoneNumber = item.optString("phoneNumber"),
        ))
    }
}

private fun JSONArray?.toPolicies(): List<AppSimPolicy> = buildList {
    if (this@toPolicies == null) return@buildList
    for (index in 0 until length()) {
        val item = optJSONObject(index) ?: continue
        val packageName = item.optString("packageName")
        if (packageName.isBlank()) continue
        val filters = item.optJSONObject("filters")
        add(AppSimPolicy(
            packageName = packageName, userId = item.optInt("userId"), uid = item.optInt("uid"),
            mode = item.optString("mode").toVisibilityMode(), profileId = item.optString("profileId").ifBlank { null },
            filters = SimFilterSet(
                operator = filters?.optBoolean("operator", true) ?: true,
                subscription = filters?.optBoolean("subscription", true) ?: true,
                cellInfo = filters?.optBoolean("cellInfo", true) ?: true,
                identifiers = filters?.optBoolean("identifiers", true) ?: true,
            ),
        ))
    }
}

private fun JSONArray?.toStrings(): List<String> = buildList {
    if (this@toStrings == null) return@buildList
    for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
}

private fun String.toNetworkType() = runCatching { SimNetworkType.valueOf(this) }.getOrDefault(SimNetworkType.LTE)
private fun String.toVisibilityMode() = runCatching { SimVisibilityMode.valueOf(this) }.getOrDefault(SimVisibilityMode.PASSTHROUGH)
