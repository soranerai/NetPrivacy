package dev.soranerai.simhide.data

import android.content.Context
import dev.soranerai.simhide.model.BuiltInSimProfiles
import dev.soranerai.simhide.model.BuiltInWifiProfiles
import dev.soranerai.simhide.model.HideConfig
import dev.soranerai.simhide.policy.ConfigCodec
import dev.soranerai.simhide.policy.TargetPolicyGrants
import java.io.File

/** Atomically persists the complete module configuration and synchronizes URI grants. */
class SimConfigStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val file = File(applicationContext.createDeviceProtectedStorageContext().filesDir, FILE_NAME)
    private val tempFile = File(file.parentFile, "$FILE_NAME.tmp")
    private val grants = TargetPolicyGrants(applicationContext)
    private val lock = Any()

    fun read(): HideConfig = synchronized(lock) {
        if (!file.exists()) return defaultConfig()
        runCatching { ConfigCodec.decode(file.readText()) }.getOrElse { defaultConfig() }
    }

    fun write(config: HideConfig): Result<Unit> = synchronized(lock) {
        val previous = read()
        file.parentFile?.mkdirs()
        tempFile.writeText(ConfigCodec.encode(config))
        if (!tempFile.renameTo(file)) {
            return@synchronized Result.failure(IllegalStateException("Unable to atomically save configuration"))
        }
        grants.sync(previous.targetPackages(), config.targetPackages())
    }

    fun restoreTargetGrants(): Result<Unit> = grants.sync(emptySet(), read().targetPackages())

    private fun defaultConfig() = HideConfig(
        simProfiles = BuiltInSimProfiles.all,
        wifiProfiles = BuiltInWifiProfiles.all,
    )
}

private fun HideConfig.targetPackages(): Set<String> =
    (simPolicies.asSequence().map { it.packageName } + wifiPolicies.asSequence().map { it.packageName }).toSet()

private const val FILE_NAME = "simhide_policy.json"
