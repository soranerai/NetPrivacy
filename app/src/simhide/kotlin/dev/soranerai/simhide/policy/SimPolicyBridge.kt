package dev.soranerai.simhide.policy

import android.os.FileObserver
import android.net.Uri
import android.os.SystemClock
import dev.soranerai.simhide.model.AppSimPolicy
import dev.soranerai.simhide.model.SimHideConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private val policyFile = File("/data/system/simhide/policy.json")

data class PolicySnapshot(val revision: Long, val config: SimHideConfig) {
    private val policiesByUid = config.appPolicies.associateBy { it.uid }
    private val profilesById = config.profiles.associateBy { it.id }

    fun policyForUid(uid: Int): AppSimPolicy? = policiesByUid[uid]
    fun profileFor(policy: AppSimPolicy) = policy.profileId?.let(profilesById::get)

    companion object { val EMPTY = PolicySnapshot(0, SimHideConfig()) }
}

/** Owns the system_server policy mirror. */
object SystemServerPolicyBridge {
    private val installed = AtomicBoolean(false)
    @Volatile private var snapshot = PolicySnapshot.EMPTY
    @Volatile private var observer: FileObserver? = null

    fun current(): PolicySnapshot = snapshot

    fun install() {
        if (!installed.compareAndSet(false, true)) return
        reload()
        observer = object : FileObserver(policyFile.parent ?: "/data/system/simhide", CLOSE_WRITE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == policyFile.name) reload()
            }
        }.also(FileObserver::startWatching)
    }

    private fun reload() {
        val candidate = runCatching { SimPolicyCodec.decode(policyFile.readText()) }.getOrNull() ?: return
        snapshot = PolicySnapshot(snapshot.revision + 1, candidate)
    }

}

/** Used in com.android.phone. Policy is obtained through the UID-gated provider. */
object PhoneProcessPolicyBridge {
    private const val POLL_INTERVAL_MS = 1_000L
    private val policyUri = Uri.parse("content://${PolicyProvider.AUTHORITY}")
    @Volatile private var snapshot = PolicySnapshot.EMPTY
    @Volatile private var lastReadAt = 0L

    fun current(): PolicySnapshot {
        val now = SystemClock.elapsedRealtime()
        if (now - lastReadAt >= POLL_INTERVAL_MS) synchronized(this) {
            if (now - lastReadAt >= POLL_INTERVAL_MS) {
                lastReadAt = now
                reload()
            }
        }
        return snapshot
    }

    fun install() {
        reload()
    }

    private fun reload() {
        val application = runCatching {
            Class.forName("android.app.ActivityThread")
                .getDeclaredMethod("currentApplication")
                .invoke(null) as? android.content.Context
        }.getOrNull() ?: return
        val response = runCatching {
            application.contentResolver.call(policyUri, PolicyProvider.METHOD_SNAPSHOT, null, null)
        }.getOrNull() ?: return
        val json = response.getString(PolicyProvider.KEY_JSON).orEmpty()
        if (json.isBlank()) return
        val candidate = runCatching { SimPolicyCodec.decode(json) }.getOrNull() ?: return
        snapshot = PolicySnapshot(response.getLong(PolicyProvider.KEY_REVISION), candidate)
    }
}
