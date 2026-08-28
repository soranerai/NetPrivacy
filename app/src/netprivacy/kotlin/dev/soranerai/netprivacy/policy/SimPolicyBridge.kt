package dev.soranerai.netprivacy.policy

import android.content.Context
import android.os.Process
import android.os.SystemClock
import dev.soranerai.netprivacy.NetPrivacyLog
import dev.soranerai.netprivacy.model.AppSimPolicy
import dev.soranerai.netprivacy.model.AppWifiPolicy
import dev.soranerai.netprivacy.model.HideConfig

data class PolicySnapshot(val revision: Long, val config: HideConfig) {
    private val policiesByUid = config.simPolicies.associateBy { it.uid }
    private val profilesById = config.simProfiles.associateBy { it.id }
    private val wifiPoliciesByUid = config.wifiPolicies.associateBy { it.uid }
    private val wifiProfilesById = config.wifiProfiles.associateBy { it.id }

    fun policyForCurrentProcess(): AppSimPolicy? = policiesByUid[Process.myUid()]
    fun profileFor(policy: AppSimPolicy) = policy.profileId?.let(profilesById::get)
    fun wifiPolicyForCurrentProcess(): AppWifiPolicy? = wifiPoliciesByUid[Process.myUid()]
    fun wifiProfileFor(policy: AppWifiPolicy) = wifiProfilesById[policy.profileId]

    companion object { val EMPTY = PolicySnapshot(0, HideConfig(simProfiles = emptyList())) }
}

/** Reads the calling application's UID-filtered snapshot after Application.attach(). */
object TargetProcessPolicyBridge {
    private const val POLL_INTERVAL_MS = 1_000L
    @Volatile private var snapshot = PolicySnapshot.EMPTY
    @Volatile private var context: Context? = null
    @Volatile private var packageName: String = ""
    @Volatile private var lastReadAt = 0L

    fun attach(context: Context, packageName: String) {
        this.context = context.applicationContext ?: context
        this.packageName = packageName
        lastReadAt = 0L
        reload()
    }

    fun current(): PolicySnapshot {
        val now = SystemClock.elapsedRealtime()
        if (now - lastReadAt >= POLL_INTERVAL_MS) synchronized(this) {
            if (now - lastReadAt >= POLL_INTERVAL_MS) reload()
        }
        return snapshot
    }

    private fun reload() {
        val appContext = context ?: return
        lastReadAt = SystemClock.elapsedRealtime()
        val response = runCatching {
            appContext.contentResolver.call(
                PolicyProvider.POLICY_URI,
                PolicyProvider.METHOD_SNAPSHOT,
                null,
                null,
            )
        }.getOrElse {
            snapshot = PolicySnapshot.EMPTY
            NetPrivacyLog.warn("target policy provider call failed for $packageName", it)
            return
        } ?: run {
            snapshot = PolicySnapshot.EMPTY
            return
        }

        val revision = response.getLong(PolicyProvider.KEY_REVISION)
        val json = response.getString(PolicyProvider.KEY_JSON).orEmpty()
        if (json.isBlank()) {
            snapshot = PolicySnapshot(revision, HideConfig(simProfiles = emptyList()))
            NetPrivacyLog.info("target policy is empty for $packageName")
            return
        }
        val candidate = runCatching { ConfigCodec.decode(json, addBuiltInsIfMissing = false) }.getOrElse {
            snapshot = PolicySnapshot.EMPTY
            NetPrivacyLog.warn("target policy snapshot is invalid for $packageName", it)
            return
        }
        snapshot = PolicySnapshot(revision, candidate)
        NetPrivacyLog.info("target policy revision=$revision, policies=${candidate.simPolicies.size}, package=$packageName")
    }
}
